#!groovy

properties([
   [$class: 'BuildDiscarderProperty',
      strategy: [$class: 'LogRotator', numToKeepStr: '10', artifactNumToKeepStr: '10']
   ]
])


docker.image('cloudbees/java-build-tools:1.0.0').inside {


    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/CloudBees-community/game-of-life']]])

    def mavenSettingsFile = "${pwd()}/.m2/settings.xml"

    stage 'Build'
    wrap([$class: 'ConfigFileBuildWrapper',
        managedFiles: [[fileId: 'maven-settings-for-gameoflife', targetLocation: "${mavenSettingsFile}"]]]) {

        sh "mvn -s ${mavenSettingsFile} clean source:jar javadoc:javadoc checkstyle:checkstyle pmd:pmd findbugs:findbugs package -DskipTests"

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

stage name:'Deploy to JBOSS', concurrency: 1
mail \
    to: 'alobato@cloudbees.com',
    subject: "Deploy version #${env.BUILD_NUMBER} on http://jboss.beesshop.org/ ?",
    body: """\
       Deploy game-of-life#${env.BUILD_NUMBER} and start web browser tests on http://jboss.beesshop.org/ ?
       Approve/reject on ${env.BUILD_URL}.
       """

//input "Deploy on http://jboss.beesshop.org/ and run Selenium tests?"
checkpoint 'Deploy to QA'

docker.image('jboss/wildfly').inside {

  // DEPLOY TO JBOSS
  def destinationWarFile = "gameoflife-${env.BUILD_NUMBER}.war"
  def versionLabel = "game-of-life#${env.BUILD_NUMBER}"
  def description = "${env.BUILD_URL}"
  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jboss-ec2', passwordVariable: 'password', usernameVariable: 'username']]) { 
    sh "/opt/jboss/wildfly/bin/jboss-cli.sh --controller=jboss.beesshop.org:9990 --connect --user=a${env.username} --password=${env.password} --command='deploy gameoflife-web/target/gameoflife.war --force'" 
  }
  
  sleep 10L // wait for JBOSS to update the status

  // WAIT FOR DEPLOYMENT
 timeout(time: 5, unit: 'MINUTES') {
      waitUntil {
          sh "/opt/jboss/wildfly/bin/jboss-cli.sh --controller=jboss.beesshop.org:9990 --connect --user=a${env.username} --password=${env.password} --command='ls /deployment=gameoflife.war'> .jboss-status"

          // parse  output
          def jbossStatus = readFile(".jboss-status")
          println "$jbossStatus"
          return jbossStatus.toLowerCase().contains("status=ok")
      }
  }
}

stage name: 'Test with Selenium', concurrency: 1

node {
    unstash 'acceptance-tests'

    // web browser tests are fragile, test up to 3 times
    retry(3) {
        docker.image('cloudbees/java-build-tools:1.0.0').inside {
            def mavenSettingsFile = "${pwd()}/.m2/settings.xml"

            wrap([$class: 'ConfigFileBuildWrapper',
                managedFiles: [[fileId: 'maven-settings-for-gameoflife', targetLocation: "${mavenSettingsFile}"]]]) {

                sh """\
                   # debug info
                   # curl http://jboss.beesshop.org/gameoflife
                   # curl -v http://localhost:4444/wd/hub
                   cd gameoflife-acceptance-tests
                   mvn -B -V -s ${mavenSettingsFile} verify -Dwebdriver.driver=remote -Dwebdriver.remote.url=http://localhost:4444/wd/hub -Dwebdriver.base.url=http://jboss.beesshop.org/gameoflife
                """
            }
        }
    }
}
