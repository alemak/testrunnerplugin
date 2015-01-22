package com.netaporter.testrunner.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskState

/**
 * Created by a.makarenko on 01/12/2014.
 */
class ExecWait extends DefaultTask {
    String command
    String ready
    String directory

    @TaskAction
    def spawnProcess() {

        ProcessBuilder builder = new ProcessBuilder(command.split(' '))
        File dir = new File("logs");
        if(!dir.exists()){
            dir.mkdir();
        }
        File log = new File("logs/" + (command.split(' '))[0] + ".log");
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        builder.directory(new File(directory))
        Process process = builder.start()

        InputStream stdout = process.getInputStream()
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(stdout))

        def line
        while ((line = reader.readLine()) != null) {
            println line
            if (line.contains(ready)) {
                println "$command is ready"
                break;
            }
        }
    }
}
class TestLifecycleListener implements TaskExecutionListener {

    def servers = ['Appium']
    @Override
    void beforeExecute(Task task) {
        if(task.name.contains("Mobile")){
            servers.each {
                println "calling start$it"
                task.project.tasks."start$it".execute()
            }
        }
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {
        if (task.name.contains("Mobile")) {
            task.project.tasks.freeAllPorts.execute()
        }
    }
}
