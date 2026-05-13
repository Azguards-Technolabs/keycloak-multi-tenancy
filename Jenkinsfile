pipeline {
    agent any

    tools {
        maven 'mvn'
    }

    environment {
        AWS_REGION = 'ap-south-1'
        CODEARTIFACT_DOMAIN = 'azguards-technolabs'
        CODEARTIFACT_REPO = 'azguards-technolabs'
        AWS_ACCOUNT_ID = '694141026695'
    }

    stages {

        stage('Checkout Code') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Azguards-Technolabs/keycloak-multi-tenancy',
                    credentialsId: 'github-token'
            }
        }

        stage('Build & Deploy') {
            steps {
                sh """
                    mvn clean deploy -DskipTests \
                    -DaltDeploymentRepository=codeartifact::default::https://${CODEARTIFACT_DOMAIN}-${AWS_ACCOUNT_ID}.d.codeartifact.${AWS_REGION}.amazonaws.com/maven/${CODEARTIFACT_REPO}/
                """
            }
        }
    }

    post {
        success {
            echo 'JAR uploaded to AWS CodeArtifact successfully.'
        }

        failure {
            echo 'Upload failed. Check logs.'
        }
    }
}