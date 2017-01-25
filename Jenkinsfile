#!groovy
docker.image('cloudbees/java-build-tools:1.0.1').inside {

    checkout scm

    stage 'Build Web App'

    withMaven(
            mavenSettingsConfig: 'maven-settings-for-gameoflife',
            mavenLocalRepo: '.repository') {

        sh "mvn -DaltSnapshotDeploymentRepository=nexus.beescloud.com::default::https://nexus.beescloud.com/content/repositories/snapshots clean source:jar deploy"
    }

    stage 'Deploy Web App On CloudFoundry'
    wrap([$class: 'CloudFoundryCliBuildWrapper',
        cloudFoundryCliVersion: 'Cloud Foundry CLI (built-in)',
        apiEndpoint: 'https://api.run-02.haas-26.pez.pivotal.io',
        skipSslValidation: true,
        credentialsId: 'pcf-elastic-runtime-credentials',
        organization: 'cloudbees',
        space: 'development']) {

           sh 'cf push gameoflife-dev -p gameoflife-web/target/gameoflife.war'
    }

    stash name: 'acceptance-tests', includes: 'gameoflife-acceptance-tests/,gameoflife-web/target/gameoflife.war'
}

stage 'Test Web App with Selenium'
mail body: "Start web browser tests on http://gameoflife-dev.cfapps.pie-23.cfplatformeng.com/ ?", subject: "Start web browser tests on http://gameoflife-dev.cfapps.pie-23.cfplatformeng.com/ ?", to: 'cleclerc@cloudbees.com'
input "Start web browser tests on http://gameoflife-dev.cfapps.pie-23.cfplatformeng.com/ ?"

checkpoint 'Web Browser Tests'

node {
    unstash 'acceptance-tests'

    // web browser tests are fragile, test up to 3 times
    retry(3) {
        docker.image('cloudbees/java-build-tools:1.0.1').inside {

          withMaven(
                  mavenSettingsConfig: 'maven-settings-for-gameoflife',
                  mavenLocalRepo: '.repository') {

                sh """
                   cd gameoflife-acceptance-tests
                   mvn verify -Dwebdriver.driver=remote -Dwebdriver.remote.driver=firefox -Dwebdriver.remote.url=http://localhost:4444/wd/hub -Dwebdriver.base.url=http://gameoflife-dev.cfapps.pie-23.cfplatformeng.com/
                """
            }
        }
    }
}
