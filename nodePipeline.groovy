#!/usr/local/bin/groovy
//plugins: build-pipeline, job-DSL, gitHub
import org.kohsuke.stapler.DataBoundSetter
GIT_AUTH_TOKEN = getGitAuthTokenVariable()

def getGitAuthTokenVariable() {
  try {
    return "${GIT_AUTH_TOKEN}"
  }
  catch (Exception ex) {
    out.println("Environment variable not found for GIT_AUTH_TOKEN")
    return "NO_AUTH"
  }
}

freeStyleJob('libotrio/libotrio-master-build'){
  description('Installs Libotrio dependencies and runs unit tests. - GROOVY GENERATED')
  scm {
    git {
        branch('origin/master')
        remote{
          url('https://'+GIT_AUTH_TOKEN+'@github.com/liatrio/libotrio.git')
        }
     }
  }
  triggers {
    githubPush()
    scm('H/2 * * * *')
  }
  wrappers{
    nodejs('Node')
  }
  steps{
    shell("npm install && npm test")
  }
  publishers {
    slackNotifier {
     notifyFailure(true)
     notifySuccess(true)
     notifyAborted(false)
     notifyNotBuilt(false)
     notifyUnstable(false)
     notifyBackToNormal(false)
     notifyRepeatedFailure(false)
     startNotification(false)
     includeTestSummary(false)
     includeCustomMessage(false)
     customMessage(null)
     buildServerUrl(null)
     sendAs(null)
     commitInfoChoice('NONE')
     teamDomain(null)
     authToken(null)
     room('liatrio')
    }
  }
}
freeStyleJob('libotrio/libotrio-deploy-staging'){
  description('Deploys master branch to Heroku staging environment. Trigger by "groovy-libotrio-build" success. - GROOVY GENERATED')
  scm {
    git {
        branch('origin/master')
        remote{
          url('https://'+GIT_AUTH_TOKEN+'@github.com/liatrio/libotrio.git')
        }
    remote{
      name('heroku-staging')
      url('git@heroku.com:libotrio-staging.git')
      credentials('eddieb-heroku')
    }
     }
  }
  triggers {
    upstream('libotrio-master-build', 'SUCCESS')
  }
  publishers {
   git{
     branch('heroku-staging','master')
     forcePush(true)
     pushOnlyIfSuccess(true)
   }
    slackNotifier {
     notifyFailure(true)
     notifySuccess(true)
     notifyAborted(false)
     notifyNotBuilt(false)
     notifyUnstable(false)
     notifyBackToNormal(false)
     notifyRepeatedFailure(false)
     startNotification(false)
     includeTestSummary(false)
     includeCustomMessage(false)
     customMessage(null)
     buildServerUrl(null)
     sendAs(null)
     commitInfoChoice('NONE')
     teamDomain(null)
     authToken(null)
     room('liatrio')
    }
  }
}
freeStyleJob('libotrio/libotrio-deploy-production'){
  description('Deploys master branch to Heroku production environment. Remotely Triggered. - GROOVY GENERATED')
  scm {
    git {
        branch('origin/master')
        remote{
          url('https://'+GIT_AUTH_TOKEN+'@github.com/liatrio/libotrio.git')
        }
        remote{
          name('heroku-production')
          url('git@heroku.com:libotrio.git')
          credentials('eddieb-heroku')
        }
     }
  }
  authenticationToken('libotrio-production-deploy')
  publishers {
   git{
     branch('heroku-production','refs/heads/master')
     forcePush(true)
     pushOnlyIfSuccess(true)
   }
    slackNotifier {
     notifyFailure(true)
     notifySuccess(true)
     notifyAborted(false)
     notifyNotBuilt(false)
     notifyUnstable(false)
     notifyBackToNormal(false)
     notifyRepeatedFailure(false)
     startNotification(false)
     includeTestSummary(false)
     includeCustomMessage(false)
     customMessage(null)
     buildServerUrl(null)
     sendAs(null)
     commitInfoChoice('NONE')
     teamDomain(null)
     authToken(null)
     room('liatrio')
    }
  }
}
buildPipelineView('libotrio/GroovyLibotrioPipeline'){
  description('A Pipeline View of the Groovy Libotrio Jobs')
  selectedJob('groovy-libotrio-build')
  displayedBuilds(3)
}
