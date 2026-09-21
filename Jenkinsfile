pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://event-booking-postgres:5432/eventbooking'
        SPRING_DATASOURCE_USERNAME = 'eventuser'
        SPRING_DATASOURCE_PASSWORD = 'eventpass'
        TESTCONTAINERS_RYUK_DISABLED = 'true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw clean compile'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw test'
            }
        }

        stage('Package') {
            steps {
                sh './mvnw package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t event-booking:${BUILD_NUMBER} .'
                sh 'docker tag event-booking:${BUILD_NUMBER} event-booking:latest'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
        }

        success {
            echo 'Pipeline completed successfully.'
        }

        failure {
            echo 'Pipeline failed.'
        }
    }
}