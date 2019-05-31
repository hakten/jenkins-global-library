#!/usr/bin/env groovy

package com.lib
import groovy.json.JsonSlurper
import hudson.EnvVars


def runPipeline() {

  /**
  *  In feature we would like to create file build.yaml
  *  1. Read all required config from build.yaml
  *  2. Build the docker images
  *  3. Unitest the application
  *  4. Push the application
  */


  def commonDeployer = new com.lib.JenkinsDeployerPipeline()
  def repositoryName = "webplatform"
  def dockerImage
  def branch = "${scm.branches[0].name}".replaceAll(/^\*\//, '').replace("/", "-").toLowerCase()


  salckChannel = 'devops'
  slackUrl = 'https://fuchicorp.slack.com/services/hooks/jenkins-ci/'
  slackTokenId = 'slack-token'

  echo "The branch name is: ${branch}"

  switch(branch) {
    case 'master':
    repositoryName = repositoryName + '-prod'
    break

    case 'qa':
    repositoryName = repositoryName +  '-qa'
    break

    case 'dev':
    repositoryName = repositoryName + '-dev'
    break

    default:
        repositoryName = null
        currentBuild.result = 'FAILURE'
        print('You are using unsupported branch name')
  }


  /**
  * This library for now building image only to webplatform application
  */
  properties([
    parameters([
      booleanParam(defaultValue: false,
        description: 'Click this if you would like to deploy to latest',
        name: 'PUSH_LATEST'
        )])])

  try {
    node {
      checkout scm



      stage('Build docker image') {

          // Build the docker image
          dockerImage = docker.build(repositoryName, "--build-arg branch_name=${branch} .")
      }

      stage('Push image') {

         // Push image to the Nexus with new release
          docker.withRegistry('https://docker.fuchicorp.com', 'nexus-private-admin-credentials') {
              dockerImage.push("0.${BUILD_NUMBER}")


              if (params.PUSH_LATEST) {
                dockerImage.push("latest")
            }
              commonDeployer.notifySuccessful()
          }
       }

       stage('clean up') {
         sh "docker rmi docker.fuchicorp.com/${repositoryName}:0.${BUILD_NUMBER} --force "
         sh "docker rmi docker.fuchicorp.com/${repositoryName}:latest --force"
         sh "rm -rf ${WORKSPACE}/*"
       }

      }
  } catch (e) {
    currentBuild.result = 'FAILURE'
    println("ERROR Detected:")
    println(e.getMessage())
    commonDeployer.notifyFailed()
  }
}


return this
