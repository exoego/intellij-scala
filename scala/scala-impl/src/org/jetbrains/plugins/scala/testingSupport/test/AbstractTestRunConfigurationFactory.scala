package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import org.jetbrains.plugins.scala.project._

abstract class AbstractTestRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez)  {
  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = super.createConfiguration(name, template).asInstanceOf[AbstractTestRunConfiguration]
    template.getProject.anyScalaModule.foreach(configuration.setModule)
    configuration
  }
}