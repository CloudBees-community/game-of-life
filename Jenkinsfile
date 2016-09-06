#!groovy

properties([
   [$class: 'BuildDiscarderProperty',
      strategy: [$class: 'LogRotator', numToKeepStr: '10', artifactNumToKeepStr: '10']
   ]
])

docker.image('cloudbees/java-build-tools:1.0.0').inside {

    checkout scm

    stage 'Build'
    withMaven(mavenSettingsConfig: 'maven-settings-for-gameoflife') {

        sh "mvn clean source:jar javadoc:javadoc checkstyle:checkstyle pmd:pmd findbugs:findbugs package"

        step([$class: 'ArtifactArchiver', artifacts: 'gameoflife-web/target/*.war'])
        step([$class: 'WarningsPublisher', consoleParsers: [[parserName: 'Maven']]])
        step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
        step([$class: 'JavadocArchiver', javadocDir: 'gameoflife-core/target/site/apidocs/'])

        // Use fully qualified hudson.plugins.checkstyle.CheckStylePublisher if JSLint Publisher Plugin or JSHint Publisher Plugin is installed
        step([$class: 'hudson.plugins.checkstyle.CheckStylePublisher', pattern: '**/target/checkstyle-result.xml'])
        // In real life, PMD and Findbugs are unlikely to be used simultaneously
        step([$class: 'PmdPublisher', pattern: '**/target/pmd.xml'])
        step([$class: 'FindBugsPublisher', pattern: '**/findbugsXml.xml'])
        step([$class: 'AnalysisPublisher'])
    }
    stash name: 'acceptance-tests', includes: 'gameoflife-acceptance-tests/,gameoflife-web/target/gameoflife.war'
}

stage name:'Deploy to AWS Beanstalk', concurrency: 1
mail \
    to: 'cleclerc@cloudbees.com',
    subject: "Deploy version #${env.BUILD_NUMBER} on http://game-of-life-qa.elasticbeanstalk.com/ ?",
    body: """\
       Deploy game-of-life#${env.BUILD_NUMBER} and start web browser tests on http://game-of-life-qa.elasticbeanstalk.com/ ?
       Approve/reject on ${env.BUILD_URL}.
       """

input "Deploy on http://game-of-life-qa.elasticbeanstalk.com/ and run Selenium tests?"
checkpoint 'Deploy to QA'

docker.image('cloudbees/java-build-tools:1.0.0').inside {

    wrap([$class: 'AmazonAwsCliBuildWrapper', credentialsId: 'aws-beanstalk-credentials', defaultRegion: 'us-east-1']) {

        // DEPLOY TO BEANSTALK
        def destinationWarFile = "gameoflife-${env.BUILD_NUMBER}.war"
        def versionLabel = "game-of-life#${env.BUILD_NUMBER}"
        def description = "${env.BUILD_URL}"
        sh """\
           aws s3 cp gameoflife-web/target/gameoflife.war s3://cloudbees-apps/$destinationWarFile
           aws elasticbeanstalk create-application-version --source-bundle S3Bucket=cloudbees-apps,S3Key=$destinationWarFile --application-name game-of-life --version-label $versionLabel --description \\\"$description\\\"
           aws elasticbeanstalk update-environment --environment-name game-of-life-qa --application-name game-of-life --version-label $versionLabel --description \\\"$description\\\"
         """
        sleep 10L // wait for beanstalk to update the HealthStatus

        // WAIT FOR BEANSTALK DEPLOYMENT
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil {
                sh "aws elasticbeanstalk describe-environment-health --environment-name game-of-life-qa --attribute-names All > .beanstalk-status.json"

                // parse `describe-environment-health` output
                def beanstalkStatusAsJson = readFile(".beanstalk-status.json")
                def beanstalkStatus = new groovy.json.JsonSlurper().parseText(beanstalkStatusAsJson)
                println "$beanstalkStatus"
                return beanstalkStatus.HealthStatus == "Ok" && beanstalkStatus.Status == "Ready"
            }
        }
    }
}

stage name: 'Test with Selenium', concurrency: 1

node {
    unstash 'acceptance-tests'

    // web browser tests are fragile, test up to 3 times
    retry(3) {
        docker.image('cloudbees/java-build-tools:1.0.0').inside {

        withMaven(mavenSettingsConfig: 'maven-settings-for-gameoflife') {


                sh """\
                   # debug info
                   # curl http://game-of-life-qa.elasticbeanstalk.com/
                   # curl -v http://localhost:4444/wd/hub

                   cd gameoflife-acceptance-tests
                   mvn verify -Dwebdriver.driver=remote -Dwebdriver.remote.url=http://localhost:4444/wd/hub -Dwebdriver.base.url=http://game-of-life-qa.elasticbeanstalk.com/
                """
            }
        }
    }
}
