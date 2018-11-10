package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import java.awt.event.ActionEvent
import java.text.MessageFormat

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionToolbarPosition, AnActionEvent}
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.{AnActionButton, CheckBoxList, JBColor, ToolbarDecorator}
import com.intellij.util.ui.{FormBuilder, JBUI}
import javax.swing.border.MatteBorder
import javax.swing.{Action, JComponent, JLabel, ListSelectionModel}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.{CompilerIndicesConfigurable, CompilerIndicesSettings}

import scala.collection.JavaConverters._

private object ImplicitUsagesSearchDialogs {
  class EnableCompilerIndicesDialog(project: Project, canBeParent: Boolean)
    extends DialogWrapper(project, canBeParent) {

    private[this] val settingsLink =
      new LinkLabel[AnyRef](ScalaBundle.message("scala.compiler.indices.settings.navigate"), null) {
        setListener({
          case (_, _) =>
            close(DialogWrapper.CLOSE_EXIT_CODE)
            ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[CompilerIndicesConfigurable])
        }, null)
      }

    private[this] val description = new JLabel(
      """|<html>Searching for implicit usages requires compiler indices to be enabled.<br>
         |Do you want to enable bytecode indexing (takes effect after IDEA restart)?</html>""".stripMargin)

    setTitle("Enable Bytecode Indexing")
    setResizable(false)
    init()

    override def createCenterPanel(): JComponent =
      FormBuilder.createFormBuilder()
        .addComponent(description)
        .addComponent(settingsLink)
        .getPanel

    override def createActions(): Array[Action] = {
      def enableCompilerIndices(): Unit = CompilerIndicesSettings(project).indexingEnabled = true

      val enable = new DialogWrapperAction("Enable") {
        override def doAction(e: ActionEvent): Unit = {
          enableCompilerIndices()
          close(DialogWrapper.OK_EXIT_CODE)
        }
      }

      val enableAndRestart = new DialogWrapperAction("Enable and restart") {
        override def doAction(e: ActionEvent): Unit = {
          enableCompilerIndices()
          ApplicationManagerEx.getApplicationEx.restart(true)
          close(DialogWrapper.OK_EXIT_CODE)
        }
      }

      Array(enableAndRestart, enable, getCancelAction)
    }
  }

  class ImplicitFindUsagesDialog(
    canBeParent:      Boolean,
    dirtyModules:     Set[Module],
    upToDateModules:  Set[Module],
    validIndexExists: Boolean,
    element:          PsiNamedElement
  ) extends DialogWrapper(element.getProject, canBeParent, DialogWrapper.IdeModalityType.PROJECT) {

    private[this] val buildDescription: String =
      s"""|<html>
          |<body>
          |Implicit usages search is only supported inside a compiled scope,<br>
          |but the use scope of member <code>{0}</code> contains dirty modules.<br>
          |<br>
          |You can:<br>
          |-&nbsp;<strong>Build</strong> some of the modules before proceeding, or<br>
          |-&nbsp;Search for usages using current indices (results may be incomplete).<br>
          |Up-to-date modules: <code>{1}</code>
          |</body>
          |</html>
          |""".stripMargin

    private[this] val rebuildDescription: String =
      s"""|<html>
          |<body>
          |Implicit usages search in only supported inside a compiled scope, <br>
          |via bytecode indices, but no valid indices exist.<br>
          |Please <strong>rebuild</strong> the project to initialise compiler indices.
          |</body>
          |</html>
          |""".stripMargin

    private[this] val dirtyModulesList = new DirtyModulesList()

    setTitle(ScalaBundle.message("find.usages.implicit.dialog.title"))
    setResizable(false)
    init()

    override def createActions(): Array[Action] = {
      val defaultActions = super.createActions()
      if (!validIndexExists)
        new DialogWrapperAction("Rebuild") {
          override def doAction(actionEvent: ActionEvent): Unit = doOKAction()
        } +: defaultActions.filterNot(getOKAction == _)
      else defaultActions
    }

    private def createDescriptionLabel: JComponent = {
      val upToDateModulesText = if (upToDateModules.isEmpty) "&lt;empty&gt;" else upToDateModules.map(_.getName).mkString(", ")
      // TODO: in case of context bound usages search element.name might not be a user-friendly identifier
      val message =
        if (validIndexExists) MessageFormat.format(buildDescription, element.name, upToDateModulesText)
        else                  rebuildDescription

      new JLabel(message)
    }

    private class DirtyModulesList() extends CheckBoxList[Module] {
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      setItems(dirtyModules.toList.asJava, _.getName)
      setItemsSelected(true)
      setBorder(JBUI.Borders.empty(3))

      def selectAllButton: AnActionButton =
        new AnActionButton("Select All", "", AllIcons.Actions.Selectall) {
          override def actionPerformed(e: AnActionEvent): Unit =
            setItemsSelected(true)
        }

      def unselectAllButton: AnActionButton =
        new AnActionButton("Unselect All", "", AllIcons.Actions.Unselectall) {
          override def actionPerformed(e: AnActionEvent): Unit =
            setItemsSelected(false)
        }

      def setItemsSelected(selected: Boolean): Unit = {
        (0 until getItemsCount).foreach(idx => setItemSelected(getItemAt(idx), selected))
        repaint()
      }

      def selectedModules: Set[Module] = (0 until getItemsCount).collect {
        case idx if isItemSelected(idx) => getItemAt(idx)
      }(collection.breakOut)
    }

    private def createModulesList: JComponent = {
      val panel = ToolbarDecorator
        .createDecorator(dirtyModulesList)
        .disableRemoveAction()
        .disableAddAction()
        .setPanelBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()))
        .addExtraAction(dirtyModulesList.selectAllButton)
        .addExtraAction(dirtyModulesList.unselectAllButton)
        .setToolbarPosition(ActionToolbarPosition.BOTTOM)
        .setToolbarBorder(JBUI.Borders.empty())
        .createPanel()

      panel.setMaximumSize(JBUI.size(-1, 300))
      panel
    }

    def moduleSelection: Set[Module] = dirtyModulesList.selectedModules

    override def createCenterPanel(): JComponent =
      if (validIndexExists)
        FormBuilder
          .createFormBuilder()
          .addVerticalGap(5)
          .addComponent(new JLabel("<html>Select modules to <strong>build</strong>:</html>"))
          .addComponent(createModulesList)
          .getPanel
      else null

    override def createNorthPanel(): JComponent =
      FormBuilder
        .createFormBuilder()
        .addComponent(createDescriptionLabel)
        .getPanel
  }
}