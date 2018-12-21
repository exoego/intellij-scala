package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText

/**
  * @author adkozlov
  */
trait ScExpressionOwnerStub[E <: PsiElement] extends StubElement[E] with PsiOwner[E] {

  def bodyText: Option[String]

  private[impl] var expressionElementReference: SofterReference[Option[ScExpression]] = null

  def bodyExpression: Option[ScExpression] = {
    getFromOptionalReference(expressionElementReference) {
      case (context, child) =>
        bodyText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    } (expressionElementReference = _)
  }
}