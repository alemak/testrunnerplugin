package com.netaporter.testrunner.gradle;
import org.gradle.api.*

class TestRunnerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("testconfig", TestRunnerPluginExtension)
        project.task('hello') << {

            println project.testconfig.message
        }
        //add listener to start appium if the test task contains word 'Mobile'
        project.gradle.addListener new TestLifecycleListener()

        project.task('WithRerun', description :'This task can be run before the test suite task to rerun the failed tests')<< {

            project.testconfig.isRerun = "true"
        }

        project.task ('startAppium', type: ExecWait, description: "Starts appium server if test task name contains 'Mobile'") {
            command 'appium'
            // ready 'LogLevel: debug'
            ready 'Device launched! Ready for commands'
            directory '.'
        }

        project.task ('freeAllPorts') << {
            def ports = [4723,5554]

            ports.each { port ->
                def cmd = "lsof -Fp -i :$port"
                def process = cmd.execute()
                process.in.eachLine { line ->
                    def killProcess = "kill -9 ${ line.substring(1) }".execute()
                    killProcess.waitFor()
                }
            }
        }

        project.tasks.addRule("Pattern: brand<BrandName> : Sets up the brand (NAP, MrP, Out) variable for the test run"){String taskName ->
            if(taskName.startsWith("brand")){
                project.task(taskName)<<{
                    println("Using brand: " + (taskName - 'brand'))
                    project.testconfig.brand = (taskName - 'brand').toLowerCase()
                }

            }
        }

        project.tasks.addRule("Pattern: webdriver<WebDriverName> : Sets up the webdriver variable for the test run"){String taskName ->
            if(taskName.startsWith("webdriver")){
                project.task(taskName)<<{
                    println("Using webdriver: " + (taskName - 'webdriver'))
                    project.testconfig.webdriver = (taskName - 'webdriver').toLowerCase()
                }
            }
        }

        project.tasks.addRule("Pattern: environment<TestEnvironmentName> : Sets up the environment (nap-napdev, mrp-mrpdev etc) variable for the test run"){String taskName ->
            if(taskName.startsWith("environment")){
                project.task(taskName)<<{
                    println("Using environment: " + (taskName - 'environment'))
                    String env = (taskName - 'environment')
                    if(project.testconfig.brand!=""){
                        project.testconfig.environment = project.testconfig.brand.toLowerCase() + ","
                    }
                    project.testconfig.environment =  project.testconfig.environment + env
                    println("setting spring.profiles.active: " + project.testconfig.environment)
                }

            }

        }

        project.tasks.addRule("Pattern: region<RegionName> : Sets up the region (INTL, AM, APAC) variable for the test run") { String taskName ->
            if (taskName.startsWith("region")) {
                project.task(taskName) << {
                    println("Using region: " + (taskName - 'region'))
                    project.testconfig.region = (taskName - 'region').toUpperCase()
                }

            }
        }
        project.tasks.addRule("Pattern: country<CountryCode> : Sets up the country (GB, US etc) variable for the test run"){String taskName ->
                if(taskName.startsWith("country")){
                    project.task(taskName)<<{
                        println("Using country: " + (taskName - 'country'))
                        project.testconfig.country = (taskName - 'country').toUpperCase()
                    }
                }
        }
        project.tasks.addRule("Pattern: suite<TestSuiteName> : Sets up the configuration name (glue, tags, path) for the test run"){String taskName ->
            if(taskName.startsWith("suite")){
                project.task(taskName)<<{
                    println("Using suite: " + (taskName - 'suite'))
                    project.testconfig.suite = (taskName - 'suite').toLowerCase()
                }

            }

        }
        //This checks if the rerun is required
        project.ext.go = {
            //If WithRerun task has been invoked before the test
            if(project.testconfig.isRerun == "true"){
                //Do the first test run
                project.testconfig.isRerun = "false";
                project.ext.runtests();
                //Now invoke rerun of the failed  tests
                project.testconfig.isRerun="true";
            }
            project.ext.runtests();
        }
        //This method is setting up the command line invocation of the cucumber tests
        project.ext.runtests = {
            project.logger.lifecycle ("Running test suite: " +
                    "\n##################################TEST SUITE#######################################\n" +
                    "BRAND : " + project.testconfig.brand.toUpperCase() +
                    (project.testconfig.region.equals("")? "": ("\nREGION : " + project.testconfig.region)) +
                    (project.testconfig.country.equals("")? "": ("\nCOUNTRY : " + project.testconfig.country)) +
                    "\nTEST ENVIRONMENT PROFILES : " + project.testconfig.environment +
                    "\nTEST SUITE : "  + project.testconfig.suite )
            project.javaexec {
                classpath = project.configurations.cucumberRuntime + project.sourceSets.main.output + project.sourceSets.test.output + project.sourceSets.test.runtimeClasspath
                ignoreExitValue = true;
                if(project.testconfig.configFile.equals("")){
                    project.testconfig.configFile = 'runconfigs/' + project.testconfig.brand.toLowerCase() + "config.groovy"
                }
                project.logger.lifecycle("Test suite configuration file used: " + project.testconfig.configFile)
                def config = new ConfigSlurper(project.testconfig.suite).parse((new File((String)project.testconfig.configFile).toURI()).toURL())
                project.logger.lifecycle("Test suite configuration:\n" + config.toString())
                //set the display env variable to use with XVFB display 1
                environment 'DISPLAY', ':1.0'

                def envString = "\n##################################PROCESS ENVIRONMENT################################\n"
                for(String key: environment.keySet()){
                    envString += key + " : " + environment.get(key) + "\n"
                }
                envString +="####################################################################################"
                project.logger.lifecycle("Process environment:" + envString)
                systemProperty 'spring.profiles.active', project.testconfig.environment
                systemProperty 'webdriver.chrome.logfile', 'logs/chromedriver.log'
                if(project.testconfig.webdriver != ""){
                    systemProperty 'webdriver',project.testconfig.webdriver
                }
                systemProperty 'generateReport', true


                if(System.getProperty('rerun')== null || System.getProperty('rerun')=="false"){
                    System.setProperty('rerun', project.testconfig.isRerun)}
                project.logger.lifecycle("Checking rerun property: " + System.getProperty('rerun'))
                if(System.getProperty('rerun')=="true"){
                    project.logger.lifecycle("Loading only the tests that failed in the previous run")
                }
                systemProperty 'rerun', System.getProperty('rerun')
                if(project.testconfig.region!="") systemProperty 'region', project.testconfig.region
                if (project.testconfig.country != ""){
                    systemProperty 'WebBotCountry', project.testconfig.country
                }
                main="cucumber.api.cli.Main"
                List<String> arglist = new ArrayList<String>();
                def regfoldername = (project.testconfig.region==""? "NonChannelized":project.testconfig.region)
                if(!project.testconfig.country=="") {
                    regfoldername = project.testconfig.country
                }
                def junitResultPath = "test-results/" + regfoldername + (System.getProperty('rerun')=="true" ? "rerun":"")+ "-results.xml"
                def jsonResultPath = "test-results/cucumber/" + regfoldername + "/cucumber" + (System.getProperty('rerun')=="true" ? "rerun":"")+ ".json"

                //used in testingUtils to search for junit test results to merge with rerun results
                systemProperty 'junit.result.file.path', junitResultPath
                //this is used by cucumber-reporting plugin to search for json file to convert to html
                systemProperty 'jsonReportPaths', jsonResultPath

                def plugins = "pretty " +" junit:"+ junitResultPath +
                        " html:test-results/cucumber/" + regfoldername +
                        " com.netaporter.test.utils.cucumber.formatters.JSONChannelizedNAPFormatter:" + jsonResultPath +
                        " rerun:" + project.testconfig.rerunFile
                def addKeyClosure = {list, key, str ->
                    try {
                        for (arg in str.tokenize()){
                            list.add(key)
                            list.add(arg)
                        }

                    }catch(Exception e){
                        throw new Exception("Exception while looking for suite " + project.testconfig.suite +
                                " in file "+ project.testconfig.configFile + " \n Could not tokenize the string: " + str + "for key: " + key + "\n" + e.stackTrace)
                    }

                }
                addKeyClosure(arglist, "--plugin", plugins)
                addKeyClosure(arglist, "--glue", config.conf.glue)
                //To utilize the output of rerun formatter set system property -Drerun=true
                if(System.getProperty("rerun") == "true"){
                    String rerunFileContents = new File((String)project.testconfig.rerunFile).text
                    println("Rerun failed tests: " + rerunFileContents)
                    if(rerunFileContents != ""){
                        def features = rerunFileContents.split(' ')
                        features.each {i ->
                            arglist.add("classpath:" + i)
                        }
                    }
                    else{
                        println("No failed tests to rerun")
                    }
                }
                //Not using rerun formatter:
                else{
                    addKeyClosure(arglist, "--tags", config.conf.tags)
                    arglist.add(config.conf.path)
                }
                args = arglist
                def clString = "\n##################################COMMAND#############################################\n"
                def isCp = false;
                for(String entry:commandLine){
                    if(entry.equals("-cp")){
                        isCp = true
                        continue;
                    }
                    if(isCp){
                        clString += "Run with '-i' flag to see the full command classpath\n"
                        isCp = false
                    }
                    else{
                        clString += entry + "\n"
                    }
                }
                clString +="####################################################################################"
                project.logger.lifecycle ("Execute command: " + clString)
                project.logger.info ("Full command: " + commandLine)
                project.testconfig.isRerun="true"
            }
        }
    }
}

class TestRunnerPluginExtension {
    String message = 'Hello from Testrunner Plugin'
    String suite = ""
    String region = ""
    String country = ""
    String environment = ""
    String webdriver = ""
    String brand = ""
    String configFile = ""
    String rerunFile = "test-results/rerun.txt"
    String isRerun = "false"
}
