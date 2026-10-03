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
        stage('Archive Artifact') {
            steps {
                echo 'Archiving application JAR...'

                archiveArtifacts(
                    artifacts: 'target/*.jar',
                    fingerprint: true,
                    onlyIfSuccessful: true
                )

                echo 'Application JAR archived successfully.'
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
                    ),
                    usernamePassword(
                        credentialsId: 'github-credentials',
                        usernameVariable: 'GITHUB_USERNAME',
                        passwordVariable: 'GITHUB_TOKEN'
                    )
                ]) {
                    sh '''
                        echo "Creating Production Release"
                        RELEASE_VERSION="release-${BUILD_NUMBER}"
                        echo "Release version: ${RELEASE_VERSION}"
                        # 1. Check Production Database
                        echo "Checking production database..."

                        if ! docker ps --format '{{.Names}}' | \
                            grep -qx "event-booking-postgres-production"; then

                            echo "Production database is not running. Starting it..."
                            docker start event-booking-postgres-production
                        else
                            echo "Production database container is already running."
                        fi


                        # 2. Wait for Production Database

                        echo "Waiting for production database to become ready..."

                        DB_READY=false

                        for i in $(seq 1 12); do

                            if docker exec event-booking-postgres-production \
                                pg_isready \
                                -U "$PROD_DB_USERNAME" \
                                -d eventbooking_prod; then

                                echo "Production database is ready."
                                DB_READY=true
                                break
                            fi

                            echo "Production database not ready yet - attempt $i/12"
                            sleep 5
                        done

                        if [ "$DB_READY" != "true" ]; then
                            echo "Production database readiness check FAILED."
                            docker logs --tail 100 event-booking-postgres-production
                            exit 1
                        fi


                        # 3. Save Current Production Image for Rollback

                        echo "Checking current production version for rollback..."

                        PREVIOUS_IMAGE=""

                        if docker inspect event-booking-production \
                            >/dev/null 2>&1; then

                            PREVIOUS_IMAGE=$(docker inspect \
                                --format='{{.Config.Image}}' \
                                event-booking-production)

                            echo "Previous production image: ${PREVIOUS_IMAGE}"
                        else
                            echo "No previous production container found."
                        fi


                        # 4. Create Release Docker Image

                        echo "Creating Docker release tag..."

                        docker tag \
                            event-booking:${BUILD_NUMBER} \
                            event-booking:${RELEASE_VERSION}


                        # 5. Remove Previous Production Container

                        echo "Removing previous production application container..."

                        docker rm -f event-booking-production \
                            2>/dev/null || true


                        # 6. Deploy New Production Version

                        echo "Deploying production application..."

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


                        # 7. Production Health Check

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


                        # 8. Automatic Rollback on Deployment Failure

                        if [ "$HEALTHY" != "true" ]; then


                            echo "Production deployment FAILED."
                            echo "Starting automatic rollback..."


                            docker logs --tail 100 \
                                event-booking-production || true

                            docker rm -f \
                                event-booking-production \
                                2>/dev/null || true

                            if [ -n "$PREVIOUS_IMAGE" ]; then

                                echo "Rolling back to ${PREVIOUS_IMAGE}..."

                                docker run -d \
                                    --name event-booking-production \
                                    --network event-booking_default \
                                    -p 8083:8080 \
                                    -e SPRING_DATASOURCE_URL=jdbc:postgresql://event-booking-postgres-production:5432/eventbooking_prod \
                                    -e SPRING_DATASOURCE_USERNAME="$PROD_DB_USERNAME" \
                                    -e SPRING_DATASOURCE_PASSWORD="$PROD_DB_PASSWORD" \
                                    -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                                    -e SPRING_PROFILES_ACTIVE=production \
                                    "$PREVIOUS_IMAGE"

                                echo "Waiting for rollback version..."

                                ROLLBACK_HEALTHY=false

                                for i in $(seq 1 12); do

                                    if curl -f --silent --show-error \
                                        http://event-booking-production:8080/actuator/health; then

                                        echo ""
                                        echo "ROLLBACK SUCCESSFUL."
                                        ROLLBACK_HEALTHY=true
                                        break
                                    fi

                                    echo "Rollback not ready yet - attempt $i/12"
                                    sleep 5
                                done

                                if [ "$ROLLBACK_HEALTHY" != "true" ]; then
                                    echo "ROLLBACK FAILED."
                                fi

                            else
                                echo "No previous production image available for rollback."
                            fi

                            exit 1
                        fi


                        echo "Production release ${RELEASE_VERSION} deployed successfully."


                        # 9. Create Git Release Tag

                        echo "Creating Git release tag..."

                        git config user.name "Jenkins CI"
                        git config user.email "jenkins@event-booking.local"

                        if git rev-parse "${RELEASE_VERSION}" \
                            >/dev/null 2>&1; then

                            echo "Git tag ${RELEASE_VERSION} already exists locally."

                        else

                            git tag -a "${RELEASE_VERSION}" \
                                -m "Production release ${RELEASE_VERSION}"

                            echo "Git tag ${RELEASE_VERSION} created."
                        fi

                        # 10. Push Git Release Tag to GitHub
                        echo "Pushing Git release tag to GitHub..."

                        printf '#!/bin/sh\ncase "$1" in\n*Username*) echo "$GITHUB_USERNAME" ;;\n*Password*) echo "$GITHUB_TOKEN" ;;\nesac\n' \
                            > .git-askpass.sh

                        chmod 700 .git-askpass.sh

                        GIT_ASKPASS="$PWD/.git-askpass.sh" \
                        GIT_TERMINAL_PROMPT=0 \
                        git push origin "${RELEASE_VERSION}"

                        PUSH_STATUS=$?

                        rm -f .git-askpass.sh

                        if [ "$PUSH_STATUS" -ne 0 ]; then
                            echo "Git release tag push FAILED."
                            exit 1
                        fi

                        echo "Git release tag ${RELEASE_VERSION} pushed successfully."


                        # Release Summary

                        echo "RELEASE SUCCESSFUL"
                        echo "Docker release: ${RELEASE_VERSION}"
                        echo "Production health: PASSED"
                        echo "Rollback protection: ENABLED"
                        echo "Git release tag: PUSHED"
                    '''
                }
            }
        }

        stage('Monitoring Verification') {
            steps {
                sh '''
                    echo "Starting Monitoring Verification"

                    # 1. Start Prometheus and Grafana
                    echo "Starting Prometheus and Grafana..."

                    docker compose up -d prometheus grafana

                    # 2. Verify Prometheus is Ready
                    echo "Waiting for Prometheus..."

                    PROM_READY=false

                    for i in $(seq 1 12); do
                        if curl -f --silent \
                            http://event-booking-prometheus:9090/-/ready; then

                            echo ""
                            echo "Prometheus is ready."
                            PROM_READY=true
                            break
                        fi

                        echo "Prometheus not ready yet - attempt $i/12"
                        sleep 5
                    done

                    if [ "$PROM_READY" != "true" ]; then
                        echo "Prometheus readiness check FAILED."
                        docker logs --tail 100 event-booking-prometheus
                        exit 1
                    fi

                    # 3. Verify Production Target is UP
                    echo "Checking Prometheus production target..."

                    TARGET_UP=false

                    for i in $(seq 1 12); do
                        RESPONSE=$(curl -s \
                            'http://event-booking-prometheus:9090/api/v1/query?query=up%7Bjob%3D%22event-booking%22%7D')

                        echo "$RESPONSE"

                        if echo "$RESPONSE" | grep -q '"1"'; then
                            echo "Prometheus production target is UP."
                            TARGET_UP=true
                            break
                        fi

                        echo "Waiting for production target - attempt $i/12"
                        sleep 5
                    done

                    if [ "$TARGET_UP" != "true" ]; then
                        echo "Prometheus production target verification FAILED."
                        exit 1
                    fi

                    # 4. Verify Alert Rule is Loaded
                    echo "Checking Prometheus alert rule..."

                    RULES=$(curl -s \
                        http://event-booking-prometheus:9090/api/v1/rules)

                    echo "$RULES"

                    if ! echo "$RULES" | grep -q "EventBookingProductionDown"; then
                        echo "EventBookingProductionDown alert rule was not found."
                        exit 1
                    fi

                    echo "Alert rule successfully loaded."

                    # 5. Simulate Production Incident
                    echo "Simulating production incident..."

                    docker stop event-booking-production

                    # Always try to restore Production if this stage fails.
                    trap 'docker start event-booking-production >/dev/null 2>&1 || true' EXIT

                    echo "Waiting for alert to fire..."

                    ALERT_FIRING=false

                    for i in $(seq 1 12); do
                        ALERTS=$(curl -s \
                            http://event-booking-prometheus:9090/api/v1/alerts)

                        echo "$ALERTS"

                        if echo "$ALERTS" | \
                            grep -q '"alertname":"EventBookingProductionDown"'; then

                            echo "EventBookingProductionDown alert is FIRING."
                            ALERT_FIRING=true
                            break
                        fi

                        echo "Alert not firing yet - attempt $i/12"
                        sleep 5
                    done

                    if [ "$ALERT_FIRING" != "true" ]; then
                        echo "Incident alert verification FAILED."
                        exit 1
                    fi

                    # 6. Recover Production
                    echo "Recovering production application..."

                    docker start event-booking-production

                    RECOVERED=false

                    for i in $(seq 1 12); do
                        if curl -f --silent --show-error \
                            http://event-booking-production:8080/actuator/health; then

                            echo ""
                            echo "Production recovered successfully."
                            RECOVERED=true
                            break
                        fi

                        echo "Production recovery pending - attempt $i/12"
                        sleep 5
                    done

                    if [ "$RECOVERED" != "true" ]; then
                        echo "Production recovery FAILED."
                        docker logs --tail 100 event-booking-production
                        exit 1
                    fi

                    # 7. Verify Prometheus Target Recovered
                    echo "Checking Prometheus target after recovery..."

                    TARGET_RECOVERED=false

                    for i in $(seq 1 12); do
                        RESPONSE=$(curl -s \
                            'http://event-booking-prometheus:9090/api/v1/query?query=up%7Bjob%3D%22event-booking%22%7D')

                        echo "$RESPONSE"

                        if echo "$RESPONSE" | grep -q '"1"'; then
                            echo "Prometheus target recovered successfully."
                            TARGET_RECOVERED=true
                            break
                        fi

                        echo "Waiting for Prometheus recovery - attempt $i/12"
                        sleep 5
                    done

                    if [ "$TARGET_RECOVERED" != "true" ]; then
                        echo "Prometheus target recovery verification FAILED."
                        exit 1
                    fi

                    trap - EXIT

                    echo "Monitoring Verification PASSED"
                    echo "Prometheus: READY"
                    echo "Production target: UP"
                    echo "Alert rule: LOADED"
                    echo "Incident alert: VERIFIED"
                    echo "Production recovery: VERIFIED"
                '''
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