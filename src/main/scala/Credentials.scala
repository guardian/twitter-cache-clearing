package com.gu.socialCacheClearing

import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest

object Credentials {
  val credentialsProvider = new AWSCredentialsProviderChain(
    new InstanceProfileCredentialsProvider(false),
    new ProfileCredentialsProvider("capi"),
    new EnvironmentVariableCredentialsProvider
  )

  val systemsManagerClient = AWSSimpleSystemsManagementClientBuilder
    .standard()
    .withCredentials(credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()

  def getCredential(name: String) = systemsManagerClient.getParameter(
    new GetParameterRequest()
      .withName("/social-cache-clearing/"+name)
      .withWithDecryption(true)
  ).getParameter.getValue
}
