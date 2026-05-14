pipeline {
    agent any

    environment {
        // This is where we put the .env file on the AWS server
        ENV_FILE = '/home/ubuntu/connecthub/.env'
        DEPLOY_DIR = '/home/ubuntu/connecthub/app'
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                // Jenkins does this automatically in the background
            }
        }

        stage('Deploy Containers') {
            steps {
                echo 'Deploying via Docker Compose...'
                // Create the deployment folder if it doesn't exist
                sh "mkdir -p ${DEPLOY_DIR}"

                // Copy the docker-compose file and all microservice folders to the deployment folder
                // Each microservice Dockerfile does a multi-stage Maven build inside Docker,
                // so we do NOT need mvnw or Java installed on the Jenkins host.
                sh "cp docker-compose.yml ${DEPLOY_DIR}/"
                sh "cp -r admin-server api-gateway auth-service config-server media-service message-service notification-service payment-service presence-service room-service service-registry websocket-service ${DEPLOY_DIR}/"

                dir("${DEPLOY_DIR}") {
                    // Copy the secret .env file we made earlier into this folder
                    sh "cp ${ENV_FILE} ./.env"

                    // Stop any old versions of the app currently running
                    sh 'docker-compose down'

                    // Build Docker images (Maven runs INSIDE each Dockerfile) and start everything!
                    // Notice we DO NOT include 'mysql' here — we use AWS RDS instead!
                    sh 'docker-compose up -d --build service-registry config-server api-gateway admin-server auth-service room-service message-service media-service presence-service notification-service websocket-service payment-service redis kafka zipkin sonarqube'
                }
            }
        }
    }
}
