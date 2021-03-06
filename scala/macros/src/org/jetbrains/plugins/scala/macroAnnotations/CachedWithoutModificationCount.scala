package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.macroAnnotations.ValueWrapper.ValueWrapper

import scala.annotation.{StaticAnnotation, tailrec}
import scala.collection.mutable.ArrayBuffer
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * If you annotate a function with @CachedWithoutModificationCount annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 10/20/15.
  */
class CachedWithoutModificationCount(valueWrapper: ValueWrapper,
                                     addToBuffer: ArrayBuffer[_ <: java.util.Map[_ <: Any , _ <: Any]]*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro CachedWithoutModificationCount.cachedWithoutModificationCountImpl
}

object CachedWithoutModificationCount {
  def cachedWithoutModificationCountImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    val analyzeCaches = analyzeCachesEnabled(c)

    def parameters: (ValueWrapper, List[Tree]) = {
      @tailrec
      def valueWrapperParam(valueWrapper: Tree): ValueWrapper = valueWrapper match {
        case q"valueWrapper = $v" => valueWrapperParam(v)
        case q"ValueWrapper.$v" => ValueWrapper.withName(v.toString)
        case q"$v" => ValueWrapper.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new CachedWithoutModificationCount(..$params)" if params.nonEmpty =>
          val valueWrapper = valueWrapperParam(params.head)
          val buffers: List[Tree] = params.drop(1)
          (valueWrapper, buffers)
        case _ => abort("Wrong parameters")
      }
    }

    //annotation parameters
    val (valueWrapper, buffersToAddTo) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //generated names
        val cacheVarName = c.freshName(name)
        val mapName = generateTermName(name.toString, "map")
        val cachedFunName = generateTermName(name.toString, "$cachedFun")
        val computedValue = generateTermName(name.toString, "computedValue")
        val cacheStatsName = generateTermName(name.toString, "cacheStats")
        val keyId = c.freshName(name.toString + "cacheKey")
        val defdefFQN = thisFunctionFQN(name.toString)

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val analyzeCachesField =
          if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
          else EmptyTree
        val wrappedRetTp: Tree = valueWrapper match {
          case ValueWrapper.None => retTp
          case ValueWrapper.WeakReference => tq"_root_.java.lang.ref.WeakReference[$retTp]"
          case ValueWrapper.SoftReference => tq"_root_.java.lang.ref.SoftReference[$retTp]"
          case ValueWrapper.SofterReference => tq"_root_.com.intellij.util.SofterReference[$retTp]"
        }

        val addToBuffers = buffersToAddTo.map { buffer =>
          q"$buffer += $mapName"
        }
        val fields = if (hasParameters) {
          q"""
            private val $mapName = new java.util.concurrent.ConcurrentHashMap[(..${flatParams.map(_.tpt)}), $wrappedRetTp]()
            ..$analyzeCachesField
            ..$addToBuffers
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: $wrappedRetTp = null.asInstanceOf[$wrappedRetTp]
            ..$analyzeCachesField
          """
        }

        def getValuesFromMap: c.universe.Tree =
          q"""
            var $cacheVarName = {
              val fromMap = $mapName.get(..$paramNames)
              if (fromMap != null) fromMap
              else null.asInstanceOf[$wrappedRetTp]
            }
            """

        def storeValue: c.universe.Tree = {
          val wrappedResult = valueWrapper match {
            case ValueWrapper.None => q"$computedValue"
            case ValueWrapper.WeakReference => q"new _root_.java.lang.ref.WeakReference($computedValue)"
            case ValueWrapper.SoftReference => q"new _root_.java.lang.ref.SoftReference($computedValue)"
            case ValueWrapper.SofterReference => q"new _root_.com.intellij.util.SofterReference($computedValue)"
          }

          if (hasParameters) q"$mapName.put((..$paramNames), $wrappedResult)"
          else q"$cacheVarName = $wrappedResult"
        }

        val getFromCache =
          if (valueWrapper == ValueWrapper.None) q"$cacheVarName"
          else {
            q"""
                if ($cacheVarName == null) null
                else $cacheVarName.get()
              """
          }

        val functionContents =
          q"""
            ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
            ..${if (hasParameters) getValuesFromMap else EmptyTree}

            val resultFromCache = $getFromCache
            if (resultFromCache == null) {
              val $computedValue = $cachedFunName()

              assert($computedValue != null, "Cached function should never return null")

              $storeValue
              $computedValue
            }
            else resultFromCache
          """

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        val updatedRhs =
          q"""
          def $cachedFunName(): $retTp = {
            $actualCalculation
          }
          $functionContents
        """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res =
          q"""
          ..$fields
          $updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}

object ValueWrapper extends Enumeration {
  type ValueWrapper = Value
  val None = Value("None")
  val SoftReference = Value("SoftReference")
  val WeakReference = Value("WeakReference")
  val SofterReference = Value("SofterReference")
}