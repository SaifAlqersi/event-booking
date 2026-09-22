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

        stage('Code Quality') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                        sh '''
                            ./mvnw sonar:sonar \
                            -Dsonar.projectKey=event-booking \
                            -Dsonar.projectName=event-booking \
                            -Dsonar.login=$SONAR_TOKEN
                        '''
                    }
                }
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

        stage('Security Scan') {
            steps {
                withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                    sh '''
                        docker run --rm \
                            --entrypoint snyk \
                            -e SNYK_TOKEN=$SNYK_TOKEN \
                            -v jenkins_home:/var/jenkins_home:ro \
                            -w /var/jenkins_home/workspace/event-booking-pipeline \
                            snyk/snyk:maven-3-jdk-21 \
                            test --file=pom.xml
                    '''
                }
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