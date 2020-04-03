#!/usr/bin/env groovy
// package com.lib
// import groovy.json.JsonSlurper
// import hudson.FilePath

  def runPipeline() {
  // def common_docker = new JenkinsDeployerPipeline()
  def gitCommitHash = ""
  def environment = ""
  def branch = "${scm.branches[0].name}".replaceAll(/^\*\//, '').replace("/", "-").toLowerCase()
  def k8slabel = "jenkins-pipeline-${UUID.randomUUID().toString()}"
  def repositoryName = "${JOB_NAME}"
      .split('/')[0]
      .replace('-fuchicorp', '')
      .replace('-build', '')
      .replace('-deploy', '')

  def deployJobName = "${JOB_NAME}"
      .split('/')[0]
      .replace('-build', '-deploy')

  if (branch.contains('dev')) {
    environment = 'dev' 
    repositoryName = repositoryName + '-' + 'dev'

  } else if (branch.contains('qa')) {
    repositoryName = repositoryName + '-' + 'qa'
    environment = 'qa' 

  } else if (branch == 'master') {
    environment = 'prod' 
  }

  properties([
      parameters([
        booleanParam(defaultValue: false,
          description: 'Click this if you would like to deploy to latest',
          name: 'PUSH_LATEST'
          )])])
// 
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
// 
  podTemplate(name: k8slabel, label: k8slabel, yaml: slavePodTemplate) {
      node(k8slabel) {
        container('fuchicorptools') {

          stage("Pulling the code") {
            checkout scm
            }

          stage('Build docker image') {
            dir("${WORKSPACE}/deployments/docker") {
              dockerImage = docker.build(repositoryName)
            }
          }

          stage("Push the Image") {
            withCredentials([usernamePassword(credentialsId: 'nexus-docker-creds', passwordVariable: 'password', usernameVariable: 'username')]) {
            sh "docker login --username ${username} --password ${password} https://docker.ggl.huseyinakten.net"
           }
            docker.withRegistry('https://docker.ggl.huseyinakten.net', 'nexus-docker-creds') {
            gitCommitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
            dockerImage.push("${gitCommitHash}") 
            
            if (params.PUSH_LATEST) {
            dockerImage.push("latest")
            }
           }
          }

          stage("Clean up") {
            sh "docker rmi --no-prune docker.ggl.huseyinakten.net/${repositoryName}:${gitCommitHash}"

            if (params.PUSH_LATEST) {
            sh "docker rmi --no-prune docker.ggl.huseyinakten.net/${repositoryName}:latest"
            }
          }

          // stage("Trigger Deploy") {
          //     build job: "${deployJobName}/master", 
          //     parameters: [
          //         [$class: 'BooleanParameterValue', name: 'terraform_apply', value: true],
          //         [$class: 'StringParameterValue', name: 'selectedDockerImage', value: "${repositoryName}:${gitCommitHash}"], 
          //         [$class: 'StringParameterValue', name: 'environment', value: "${environment}"]
          //         ]
          //  }
        }
    }
  }
}


//fuchicorp remove
//everyting triggers master