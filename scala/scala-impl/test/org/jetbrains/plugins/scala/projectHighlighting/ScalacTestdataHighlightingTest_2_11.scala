package org.jetbrains.plugins.scala
package projectHighlighting

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */

@Category(Array(classOf[ScalacTests]))
class ScalacTestdataHighlightingTest_2_12 extends ScalacTestdataHighlightingTestBase_2_12 {

  override val reporter = ProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems = Map.empty, reportStatus = false)

  override def filesToHighlight: Array[File] = {
    val testDataPath = TestUtils.getTestDataPath + "/scalacTests/pos/"

    val dir = new File(testDataPath)
    dir.listFiles()
  }

  def testScalacTests(): Unit = doTest()

}

abstract class ScalacTestdataHighlightingTestBase_2_12
  extends ScalaLightCodeInsightFixtureTestAdapter with SeveralFilesHighlightingTest  {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

  override def getProject = super.getProject

  override def getModule: Module = super.getModule

  override def librariesLoaders = Seq(
    ScalaSDKLoader(includeScalaReflect = true)
  )
}