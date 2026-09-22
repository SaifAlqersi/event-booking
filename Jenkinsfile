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

                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
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
        stage('Deploy') {

            steps {

                sh '''
                echo "Deploying event-booking to staging environment..."

                docker rm -f event-booking-staging 2>/dev/null || true

                docker run -d \
                    --name event-booking-staging \
                    --network event-booking_default \
                    -p 8082:8080 \
                    -e SPRING_DATASOURCE_URL=jdbc:postgresql://event-booking-postgres:5432/eventbooking \
                    -e SPRING_DATASOURCE_USERNAME=eventuser \
                    -e SPRING_DATASOURCE_PASSWORD=eventpass \
                    -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                    event-booking:${BUILD_NUMBER}


                echo "Waiting for application startup..."

                sleep 20

                docker ps --filter name=event-booking-staging

                docker logs --tail 50 event-booking-staging
                '''
            }
        }
        stage('Release') {
            steps {
                sh '''
                    echo "Creating production release..."

                    RELEASE_VERSION="release-${BUILD_NUMBER}"

                    echo "Release version: ${RELEASE_VERSION}"

                    docker tag \
                        event-booking:${BUILD_NUMBER} \
                        event-booking:${RELEASE_VERSION}

                    docker rm -f event-booking-production 2>/dev/null || true

                    docker run -d \
                        --name event-booking-production \
                        --network event-booking_default \
                        -p 8083:8080 \
                        -e SPRING_DATASOURCE_URL=jdbc:postgresql://event-booking-postgres:5432/eventbooking \
                        -e SPRING_DATASOURCE_USERNAME=eventuser \
                        -e SPRING_DATASOURCE_PASSWORD=eventpass \
                        -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                        -e SPRING_PROFILES_ACTIVE=production \
                        event-booking:${RELEASE_VERSION}

                    echo "Waiting for production application..."
                    sleep 20

                    docker ps --filter name=event-booking-production

                    docker inspect \
                        -f '{{.State.Running}}' \
                        event-booking-production | grep true

                    echo "Production release ${RELEASE_VERSION} deployed successfully."
                '''
            }
        }
        stage('Monitoring Verification') {

            steps {

                sh '''
                echo "Checking production health..."

                sleep 10

                curl -f http://localhost:8083/actuator/health

                echo "Production health check passed."
                '''
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