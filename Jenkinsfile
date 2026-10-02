pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://event-booking-postgres:5432/eventbooking'
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
                withCredentials([
                    usernamePassword(
                        credentialsId: 'event-booking-db',
                        usernameVariable: 'DB_USERNAME',
                        passwordVariable: 'DB_PASSWORD'
                    )
                ]) {
                    sh './mvnw test'
                }
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
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dsonar.token=$SONAR_TOKEN
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
                withCredentials([
                    usernamePassword(
                        credentialsId: 'event-booking-db',
                        usernameVariable: 'DB_USERNAME',
                        passwordVariable: 'DB_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "Deploying event-booking to staging environment..."

                        docker rm -f event-booking-staging 2>/dev/null || true

                        docker run -d \
                            --name event-booking-staging \
                            --network event-booking_default \
                            -p 8082:8080 \
                            -e SPRING_DATASOURCE_URL=jdbc:postgresql://event-booking-postgres:5432/eventbooking \
                            -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
                            -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
                            -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                            event-booking:${BUILD_NUMBER}

                        echo "Waiting for staging application to become healthy..."

                        HEALTHY=false

                        for i in $(seq 1 12); do
                            if curl -f --silent --show-error \
                                http://event-booking-staging:8080/actuator/health; then

                                echo ""
                                echo "Staging health check PASSED."
                                HEALTHY=true
                                break
                            fi

                            echo "Staging not ready yet - attempt $i/12"
                            sleep 5
                        done

                        if [ "$HEALTHY" != "true" ]; then
                            echo "Staging health check FAILED."
                            docker logs --tail 100 event-booking-staging
                            exit 1
                        fi

                        echo "Staging deployment completed successfully."
                    '''
                }
            }
        }
        stage('Release') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'event-booking-prod-db',
                        usernameVariable: 'PROD_DB_USERNAME',
                        passwordVariable: 'PROD_DB_PASSWORD'
                    )
                ]) {
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
                            -e SPRING_DATASOURCE_URL=jdbc:postgresql://event-booking-postgres-production:5432/eventbooking_prod \
                            -e SPRING_DATASOURCE_USERNAME="$PROD_DB_USERNAME" \
                            -e SPRING_DATASOURCE_PASSWORD="$PROD_DB_PASSWORD" \
                            -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                            -e SPRING_PROFILES_ACTIVE=production \
                            event-booking:${RELEASE_VERSION}

                        echo "Waiting for production application to become healthy..."

                        HEALTHY=false

                        for i in $(seq 1 12); do
                            if curl -f --silent --show-error \
                                http://event-booking-production:8080/actuator/health; then

                                echo ""
                                echo "Production health check PASSED."
                                HEALTHY=true
                                break
                            fi

                            echo "Production not ready yet - attempt $i/12"
                            sleep 5
                        done

                        if [ "$HEALTHY" != "true" ]; then
                            echo "Production health check FAILED."
                            docker logs --tail 100 event-booking-production
                            exit 1
                        fi

                        echo "Production release ${RELEASE_VERSION} deployed successfully."
                    '''
                }
            }
        }
        stage('Monitoring Verification') {

            steps {

                sh '''
                echo "Checking production health..."

                sleep 10

                curl -f http://event-booking-production:8080/actuator/health

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