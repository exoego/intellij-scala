package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util._

object Expression{

/*
SIMPLE EXPRESSION
Default grammar:
SimpleExpr ::= Literal
              | Path
              | �(� [Expr] �)�
              | BlockExpr
              | new Template
              | SimpleExpr �.� id
              | SimpleExpr TypeArgs
              | SimpleExpr ArgumentExprs
              | XmlExpr

*******************************************

Realized grammar:
SimpleExpr ::= Literal
              | SimpleExpr �.� id

*******************************************

FIRST(SimpleExpr) = ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN

*/
  val SIMPLE_FIRST = BNF.tLITERALS

  def parseSimpleExpr(builder : PsiBuilder) : Boolean = {

    if (BNF.tLITERALS.contains(builder.getTokenType)) {
      Literal parse (builder) // Ate literal
      subParseSimpleExpr(builder)
    } else {
      builder.error("Wrong expression!")
      false
    }
  }

  def subParseSimpleExpr(builder : PsiBuilder) : Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => {
        val dotMarker = builder.mark()
        builder.advanceLexer()
        dotMarker.done(ScalaElementTypes.DOT)
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementTypes.IDENTIFIER)
            subParseSimpleExpr(builder)
          }
          case _ => {
            builder.error("Identifier expected")
            false
          }
        }
      }
      case _ => true
    }
  }

/*
PREFIX EXPRESSION
Default grammar
PrefixExpr ::= [ �-� | �+� | �~� | �!� ] SimpleExpr

***********************************************

Realized grammar:
PrefixExpr ::= [ �-� | �+� | �~� | �!� ] SimpleExpr

*******************************************

FIRST(PrefixExpression) = ScalaTokenTypes.tPLUS
                          ScalaTokenTypes.tMINUS
                          ScalaTokenTypes.tTILDA
                          ScalaTokenTypes.tNOT
     union                SimpleExpression.FIRST
*/

  val PREFIX_FIRST = TokenSet.orSet(Array(SIMPLE_FIRST, BNF.tPREFIXES ))

  def parsePrefixExpr(builder : PsiBuilder) : Boolean = {

    val marker = builder.mark()
    var result = false

    builder getTokenType match {
      case ScalaTokenTypes.tPLUS
           | ScalaTokenTypes.tMINUS
           | ScalaTokenTypes.tTILDA
           | ScalaTokenTypes.tNOT => {
             val prefixMarker = builder.mark()
             builder.advanceLexer()
             prefixMarker.done(ScalaElementTypes.PREFIX)
             if (SIMPLE_FIRST.contains(builder.getTokenType)) {
               result = parseSimpleExpr (builder)
             } else {
              builder.error("Wrong prefix expression!")
              result = false
             }
           }
      case _ => result = parseSimpleExpr (builder)
    }
    marker.done(ScalaElementTypes.PREFIX_EXPR)
    result
  }

/*
INFIX EXPRESSION
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

FIRST(InfixExpression) =  ScalaTokenTypes.tIDENTIFIER
     union                PrefixExpression.FIRST

*/

  val INFIX_FIRST = PREFIX_FIRST

  def parseInfixExpr(builder : PsiBuilder) : Boolean = {
    val marker = builder.mark()
    var result = parsePrefixExpr(builder)
    if (result) {
      result = subParseInfixExpr (builder)
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
    else {
      builder.error("Wrong infix expression!")
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
  }

  def subParseInfixExpr(builder : PsiBuilder) : Boolean = {

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {

        val idMarker = builder.mark()
        builder.advanceLexer()
        idMarker.done(ScalaElementTypes.IDENTIFIER)

        ParserUtils.rollForward(builder)
        if (parsePrefixExpr(builder)) {
          subParseInfixExpr(builder)
        } else {
          builder.error("Wrong infix expression!")
          false
        }
      }
      case _ => true
    }
  }


}