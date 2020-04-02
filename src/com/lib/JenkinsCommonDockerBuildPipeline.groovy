#!/usr/bin/env groovy
// package com.lib
// import groovy.json.JsonSlurper
// import hudson.FilePath

  def runPipeline() {
  // def common_docker = new JenkinsDeployerPipeline()
  def environment = ""
  def gitCommitHash = ""
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

  sh "echo ${repositoryName}"
  sh "echo ${deployJoName}"







}