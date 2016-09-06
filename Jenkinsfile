
docker.image('cloudbees/java-build-tools:1.0.0').inside {
    checkout scm

    stage 'Build Web App'
    withMaven(mavenSettingsConfig: 'maven-settings-for-gameoflife') {
        sh "mvn clean package"
        step([$class: 'ArtifactArchiver', artifacts: 'gameoflife-web/target/*.war'])
    }
}

docker.withRegistry('', 'dockerhub-credentials') {
    // build docker image 'cleclerc/game-of-life' and push it to docker hub
    stage 'Build & Push Docker Image'
    def gameOfLifeImage = docker.build('cleclerc/game-of-life', 'gameoflife-web')
    gameOfLifeImage.push()
}

stage 'Deploy ECS Service'
mail \
    to: 'cleclerc@cloudbees.com',
    subject: "Deploy version #${env.BUILD_NUMBER} on http://gameoflife-ecs.beesshop.org/ ?",
    body: """\
       Deploy game-of-life#${env.BUILD_NUMBER} and start web browser tests on http://gameoflife-ecs.beesshop.org/ ?
       Approve/reject on ${env.BUILD_URL}.
       """


input "Deploy on http://gameoflife-ecs.beesshop.org/ and run Selenium tests?"
checkpoint 'Deploy to QA'

docker.image('cloudbees/java-build-tools:1.0.0').inside {
    wrap([$class: 'AmazonAwsCliBuildWrapper', credentialsId: 'aws-cleclerc-admin', defaultRegion: 'us-east-1']) {
        // TODO THESE ARE PROBABLY NOT THE BEST ECS CALLS
        sh "aws ecs update-service --service game-of-life --desired-count 0"
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil {
                sh "aws ecs describe-services --services game-of-life > .amazon-ecs-service-status.json"

                // parse `describe-services` output
                def ecsServicesStatusAsJson = readFile(".amazon-ecs-service-status.json")
                def ecsServicesStatus = new groovy.json.JsonSlurper().parseText(ecsServicesStatusAsJson)
                println "$ecsServicesStatus"
                def ecsServiceStatus = ecsServicesStatus.services[0]
                return ecsServiceStatus.get('runningCount') == 0 && ecsServiceStatus.get('status') == "ACTIVE"
            }
        }
        sh "aws ecs update-service --service game-of-life --desired-count 1"
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil {
                sh "aws ecs describe-services --services game-of-life > .amazon-ecs-service-status.json"

                // parse `describe-services` output
                def ecsServicesStatusAsJson = readFile(".amazon-ecs-service-status.json")
                def ecsServicesStatus = new groovy.json.JsonSlurper().parseText(ecsServicesStatusAsJson)
                println "$ecsServicesStatus"
                def ecsServiceStatus = ecsServicesStatus.services[0]
                return ecsServiceStatus.get('runningCount') == 0 && ecsServiceStatus.get('status') == "ACTIVE"
            }
        }
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil {
                try {
                    sh "curl http://gameoflife-ecs.beesshop.org/"
                    return true
                } catch (Exception e) {
                    return false
                }
            }
        }
        echo "game-of-life#${env.BUILD_NUMBER} SUCCESSFULLY deployed to http://gameoflife-ecs.beesshop.org/"
    }
}

stage 'Web Browser tests'
retry(3) { // web browser tests are fragile, test up to 3 times
    docker.image('cloudbees/java-build-tools:1.0.0').inside {
       withMaven(mavenSettingsConfig: 'maven-settings-for-gameoflife') {
            sh """
                cd gameoflife-acceptance-tests
                mvn verify -Dwebdriver.driver=remote -Dwebdriver.remote.url=http://localhost:4444/wd/hub -Dwebdriver.base.url=http://gameoflife-ecs.beesshop.org
            """
        }
    }
}
