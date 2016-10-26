/* 
    To build and deploy on Azure please ensure the following:
    1) An Ubuntu 14.04 slave with the label "linux". Must have JDK 7 and Git installed
    2) Set up a Maven tool installer called "maven-3.3"
    3) Set up environment variables for:
         azureHost : the host to use for deployment - from the Azure console
         svchost : the public DNS name the app will be visible on
    4) Credentials with the id of "azure-deployment-id" containing the ftps user:password for deployment

*/

node ("linux") {

    def local_path="gameoflife-web/target"
    def war="gameoflife.war"
    def target="/site/wwwroot/webapps"

    stage "Checkout"
    git branch: 'azure-pipeline', url: 'https://github.com/harniman/game-of-life'

    stage "Build"

    withEnv(["PATH=${tool 'maven-3.3'}/bin:${env.PATH}"]) {
        sh 'mvn clean package'
    }
    
    stage "Deploy to Azure"

    withCredentials([[$class: 'UsernamePasswordBinding', 
        credentialsId: 'azure-deployment-id', 
        variable: '_userpass']]) {

        sh "curl -T ${local_path}/${war} ftps://\"\$env._userpass\"@\$azureHost${target}/"
    }
    
    stage "Verify deployment"
    
    retry(count: 5) { 
        echo "Checking for the application at ${env.svchost}/gameoflife"
        sh 'sleep 5 && curl -f $svchost/gameoflife'
    }

}
