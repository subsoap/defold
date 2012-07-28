package com.dynamo.cr.editor.ui;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;

import com.dynamo.cr.editor.ui.tip.TipManager;

/**
 * Main window abstraction. Responsible for creating the top-level window.
 *
 * |---------------------------------------------
 * |                 Message Area               |
 * |--------------------------------------------|
 * |                                            |
 * |                                            |
 * |                Page composite              |
 * |                                            |
 * |                                            |
 * |--------------------------------------------|
 * | Progress |                    Status Line  |
 * |--------------------------------------------|
 *
 * @author chmu
 *
 */
public class EditorWindow  {

    private IWorkbenchWindowConfigurer windowConfigurer;
    private Shell shell;
    private Control tipControl;
    private TipManager tipManager;

    public EditorWindow(IWorkbenchWindowConfigurer windowConfigurer) {
        this.windowConfigurer = windowConfigurer;
    }

    public IWorkbenchWindowConfigurer getWindowConfigurer() {
        return windowConfigurer;
    }

    public void setMessageAreaVisible(boolean visible) {
        ((GridData) tipControl.getLayoutData()).heightHint = visible ? SWT.DEFAULT : 0;
        shell.layout();
    }

    public void createContents(final Shell shell) {
        this.shell = shell;
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        layout.horizontalSpacing = 0;
        shell.setLayout(layout);
        ApplicationWindow window = (ApplicationWindow) getWindowConfigurer().getWindow();
        Menu menuBar = window.getMenuBarManager().createMenuBar((Decorations) shell);
        shell.setMenuBar(menuBar);

        tipManager = new TipManager(this, getWindowConfigurer().getWindow().getPartService());
        tipControl = tipManager.createControl(shell);
        tipControl.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.FILL).create());

        Control pageComposite = getWindowConfigurer().createPageComposite(shell);
        pageComposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).create());

        Composite statusComposite = new Composite(shell, SWT.NONE);
        statusComposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.FILL).create());
        GridLayout statusLayout = new GridLayout(2, true);
        statusComposite.setLayout(statusLayout);

        SimpleProgressProvider progressProvider = new SimpleProgressProvider();
        Job.getJobManager().setProgressProvider(progressProvider);
        Control progressControl = progressProvider.createControl(statusComposite);
        progressControl.setLayoutData(GridDataFactory.fillDefaults().grab(false, false).align(SWT.BEGINNING, SWT.CENTER).hint(120, SWT.DEFAULT).create());

        Control statusLine = getWindowConfigurer().createStatusLineControl(statusComposite);
        statusLine.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.FILL).create());

        shell.layout(true);
        shell.pack(true);
    }

    public void dispose() {
        tipManager.dispose();
    }

}
