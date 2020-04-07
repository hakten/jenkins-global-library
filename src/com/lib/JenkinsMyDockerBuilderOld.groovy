
  def k8slabel = "jenkins-pipeline-${UUID.randomUUID().toString()}"
  def repositoryName = "${JOB_NAME}"
      .split('/')[0]
      .replace('-fuchicorp', '')
      .replace('-build', '')
      .replace('-deploy', '')

    properties([
      parameters([
        string(defaultValue: 'https://registry.hub.docker.com', 
            description: 'Please enter docker registry address.', 
            name: 'DOCKER_URL', trim: false),
        string(defaultValue: '', 
            description: 'Enter docker repository name as in fuchicorp/artemis or artemis format.', 
            name: 'REPO_NAME', trim: false),
        string(defaultValue: '', 
            description: 'Enter application version number.', 
            name: 'VERSION', trim: false),
        booleanParam(defaultValue: true,
            description: 'Click this if you would like to build latest version.',
            name: 'LATEST')
            ])])

    def slavePodTemplate = """
      metadata:
        labels:
          k8s-label: ${k8slabel}
        annotations:
          jenkinsjoblabel: ${env.JOB_NAME}-${env.BUILD_NUMBER}
      spec:
        affinity:
          podAntiAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                - key: component
                  operator: In
                  values:
                  - jenkins-jenkins-master
              topologyKey: "kubernetes.io/hostname"
        containers:
        - name: docker
          image: docker:latest
          imagePullPolicy: Always
          command:
          - cat
          tty: true
          volumeMounts:
            - mountPath: /var/run/docker.sock
              name: docker-sock
            - mountPath: /etc/secrets/service-account/
              name: google-service-account
        - name: fuchicorptools
          image: fuchicorp/buildtools
          imagePullPolicy: Always
          command:
          - cat
          tty: true
          volumeMounts:
            - mountPath: /var/run/docker.sock
              name: docker-sock
            - mountPath: /etc/secrets/service-account/
              name: google-service-account
        serviceAccountName: common-service-account
        securityContext:
          runAsUser: 0
          fsGroup: 0
        volumes:
          - name: google-service-account
            secret:
              secretName: google-service-account
          - name: docker-sock
            hostPath:
              path: /var/run/docker.sock
    """

  podTemplate(name: k8slabel, label: k8slabel, yaml: slavePodTemplate) {
      node(k8slabel) {
        container('fuchicorptools') {


          stage("Pulling the code") {
            sh "echo hello"
            checkout([$class: 'GitSCM', branches: [[name: '*/dev']], 
            doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], 
            userRemoteConfigs: [[url: 'https://github.com/fuchicorp/smart-profile.git']]])
          }

          stage("Building the image") {
            dir("${WORKSPACE}/deployments/docker") {
              dockerImage = docker.build(repositoryName)
            }
          }

          stage("Push the Image") {
            withCredentials([usernamePassword(credentialsId: 'nexus-docker-creds', passwordVariable: 'password', usernameVariable: 'username')]) {
            sh "docker login --username ${username} --password ${password} https://docker.gcp.huseyinakten.net"
           }

            if ( params.LATEST ) {
                        docker.withRegistry('https://docker.gcp.huseyinakten.net', 'nexus-docker-creds') {
                        dockerImage.push("latest")
            }
          }    
            if ( !params.LATEST ) {
                if ( params.VERSION) {
                        docker.withRegistry('https://docker.gcp.huseyinakten.net', 'nexus-docker-creds') {
                        dockerImage.push("${VERSION}")
              }
            }
          }  
          if ( !params.LATEST ) {
              if ( !params.VERSION) {
                  sh "echo Please choose 'latest' or enter a version number."
                  currentStage.result = 'FAILURE'
                  currentBuild.result = 'FAILURE'
              }
            }
         }
    } 
  }
}

