import groovy.json.JsonSlurper
import java.io.FileReader;
import java.nio.file.*
import groovy.json.*
import groovy.runtime.*;

USE_FOLDERS = getFoldersVariable() //Feature flag which assumes you're using the Folders plugin - false disables it and displays all jobs at top level
DEV_BOX = getDevBoxVariable() //Add the evironment variable "${DEV_BOX}"
GIT_AUTH_TOKEN = getGitAuthTokenVariable()//Add the environment variable "${GIT_AUTH_TOKEN}" - Required for private repos
GIT_API = "https://api.github.com/repos/"
GIT_URL = getGitUrl() //Build the URL based on the auth token if provided

boolean getFoldersVariable() {
  try {
    out.println("USE_FOLDERS environment variable = " + "${USE_FOLDERS}")
    def useFolders = "${USE_FOLDERS}"
    return useFolders.toBoolean()
  }
  catch (Exception ex) {
    out.println("Environment variable not found for USE_FOLDERS.  Defaulting to standard job list")
    return false // Defaulting to no FOLDERS
  }
}

boolean getDevBoxVariable() {
  try {
    out.println("DEV_BOX environment variable = " + "${DEV_BOX}")
    def devBox = "${DEV_BOX}"
    return devBox.toBoolean()
  }
  catch (Exception ex) {
    out.println("Environment variable not found for DEV_BOX")
    return true // Defaulting to a DEV_BOX to keep the builds and deployments disabled locally
  }
}

def getGitAuthTokenVariable() {
  try {
    return "${GIT_AUTH_TOKEN}"
  }
  catch (Exception ex) {
    out.println("Environment variable not found for GIT_AUTH_TOKEN")
    return "NO_AUTH"
  }
}

def getGitUrl() {
  if (GIT_AUTH_TOKEN.size() > 10)
  {
    return "https://"+GIT_AUTH_TOKEN+"@github.com/"
  }
  return "https://github.com/"
}

String fileName = "buildDeployPipelines.json"
def file = readFileFromWorkspace(fileName)
def inputJson = new JsonSlurper().parseText(file)

def components =  inputJson.components
for( component in components ) {

  if (USE_FOLDERS)
  {
    USE_FOLDERS = createFolders(component.scmProject, component.productName)
    out.println("Using folders = " + USE_FOLDERS)
    //should be true if the plugin exists
  }
  
  def deploymentEnvironments = component.deploymentEnvironments
  for(env in deploymentEnvironments) {
    createDeployJob(component.productName, component.scmProject, env)
  }
  createBuildJob( component )
}

def createFolders(project, product)
{
  def productPath = project + "/" + product
  try {
    def createProjectFolder = folder(project)
    def createProductFolder = folder(productPath)
    def createProductBuildsFolder = folder(productPath + "/builds")
    def createProductDeploymentsFolder = folder(productPath + "/deployments")
  }
  catch (Exception exception) {
    return false
  }
  return true
}

def createDeployJob(productName, projectName, environment) {
  def deployJobName = createDeployJobName(projectName, productName, environment)
  def jobLocation = ""

  if (USE_FOLDERS)
  {
    jobLocation = projectName + "/"+ productName + "/deployments/"
  }

  job(jobLocation + deployJobName) {
    if(DEV_BOX == true)
    {
      disabled()
    }
    description("<h3>This job was created by automation.  Manual edits to this job are discouraged.</h3> ")
    steps {
      label('master')
    }
    publishers {
        s3('drew') {
            entry('*/target/*.jar,*UI/dist/**', 'build.liatrio.com', 'us-west-2') {
              storageClass('STANDARD')
              noUploadOnFailure()
              uploadFromSlave()
              managedArtifacts(true)
            }
        }
        slackNotifier {
            notifyFailure(true)
            notifySuccess(true)
            notifyAborted(false)
            notifyNotBuilt(false)
            notifyUnstable(false)
            notifyBackToNormal(true)
            notifyRepeatedFailure(false)
            startNotification(true)
            includeTestSummary(true)
            includeCustomMessage(false)
            customMessage(null)
            buildServerUrl(null)
            sendAs(null)
            commitInfoChoice('AUTHORS_AND_TITLES')
            teamDomain(null)
            authToken(null)
            room('jenkins-build')
        }
        mailer('drew@liatrio.com', true, true)
        githubCommitNotifier()
    }

  }
  return deployJobName
}

def createDeployJobName(projectName, productName , environment) {
  return (projectName + "-" + productName +"-deploy-" + environment).toLowerCase()
}

def getBranches(branchApi) {
  def auth = GIT_AUTH_TOKEN
  def json = new JsonSlurper()

  if (auth.size() > 20) //Just looking for something that looks real
  {
    out.println("The git auth token was provided.  Using it...")
    try {
      return json.parse(branchApi.toURL().newReader(requestProperties: ["Authorization": "token ${auth}".toString(), "Accept": "application/json"]))
    }
    catch (Exception ex) {
      out.println(ex)
      return null //API request failed
    }
  }
  else
  {
    try {
      return json.parse(branchApi.toURL().newReader())
    }
    catch (Exception ex) {
      out.println(ex)
      out.println("Auth likely failed - Provide an api key if repository is private.")
      return null //API request failed
    }
  }
}

def createBuildJob(component) {
  String productPath = component.scmProject + "/" + component.productName
  String branchApi =  GIT_API + productPath + "/branches"
  String repoUrl = GIT_URL + productPath

  def ciEnvironments = component.ciEnvironments
  def downStreamJobs = []

  for(env in ciEnvironments) {
    if (USE_FOLDERS) {
      downStreamJobs.add("../deployments/" + createDeployJobName(component.scmProject, component.productName, env))
    }
    else {
      downStreamJobs.add(createDeployJobName(component.scmProject, component.productName, env))
    }
  }

  def branches = getBranches(branchApi)
  if (branches)
  {
    branches.each {
        def branchName = it.name
        def jobName = "${component.scmProject}-${component.productName}-${branchName}-build".replaceAll('/','-').toLowerCase()
        def jobLocation = ""
        if (USE_FOLDERS) {
          jobLocation = productPath + "/builds/"
        }
        out.println("Creating or updating job " + jobLocation )
        mavenJob(jobLocation + jobName) {
            if(DEV_BOX == true)
            {
              disabled()
            }
            description("<h3>This job was created with automation.  Manual edits to this job are discouraged.</h3> ")
            scm {
                git(repoUrl, branchName)
            }

            triggers {
              scm('H/2 * * * *')
              githubPush()
            }
            publishers {
                slackNotifier {
                    notifyFailure(true)
                    notifySuccess(true)
                    notifyAborted(false)
                    notifyNotBuilt(false)
                    notifyUnstable(false)
                    notifyBackToNormal(true)
                    notifyRepeatedFailure(false)
                    startNotification(true)
                    includeTestSummary(true)
                    includeCustomMessage(false)
                    customMessage(null)
                    buildServerUrl(null)
                    sendAs(null)
                    commitInfoChoice('AUTHORS_AND_TITLES')
                    teamDomain(null)
                    authToken(null)
                    room('jenkins-build')
                }
                mailer('drew@liatrio.com', true, true)
                githubCommitNotifier()
            }
            mavenInstallation('maven 3')

            if ( branchName == "master" )
              goals("clean install")
            else
              goals("clean install")

            postBuildSteps('SUCCESS') {
              if( downStreamJobs && branchName == "master" ) {
                downstreamParameterized {
                  trigger(downStreamJobs.join(", "))
                }
              }
            }
          }
      return jobName
    }
  }
}
