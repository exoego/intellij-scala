package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex

case class GlobalImplicitConversion(containingObject: ScObject, function: ScFunction) {

  def toImplicitConversionData: Option[ImplicitConversionData] = TypeDefinitionMembers
    .getSignatures(containingObject)
    .forName(function.name)
    .findNode(function)
    .map(_.info.substitutor)
    .flatMap(ImplicitConversionData(function, _))
}

object GlobalImplicitConversion {

  private[implicits] def collectIn(elementScope: ElementScope): Iterable[GlobalImplicitConversion] = {
    val ElementScope(project, scope) = elementScope
    val manager = ScalaPsiManager.instance(project)

    def containingObjects(function: ScFunction): Set[ScObject] =
      Option(function.containingClass)
        .fold(Set.empty[ScObject])(manager.inheritorOrThisObjects)

    for {
      member <- ImplicitConversionIndex.allElements(scope)(project)

      function <- member match {
        case f: ScFunction => f :: Nil
        case c: ScClass => c.getSyntheticImplicitMethod.toList
        case _ => Nil
      }

      obj <- containingObjects(function)
      if obj.qualifiedName != "scala.Predef"
    } yield GlobalImplicitConversion(obj, function)
  }

}