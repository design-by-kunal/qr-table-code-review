package com.gulfnet.shared_library.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Autowired
    QRTableGenericConfig qrtableGenericServiceConfig;

    /**
     * Creates and configures an Amazon S3 client bean using AWS credentials and region
     * from the configuration. Validates that all required AWS properties are configured
     * before creating the client.
     *
     * @return configured AmazonS3 client instance
     * @throws IllegalStateException if AWS configuration is missing or incomplete
     */
    @Bean
    public AmazonS3 s3client() {
        if (!isAwsConfigured()) {
            throw new IllegalStateException("AWS configuration is missing. Please configure AWS properties.");
        }
        
        AWSCredentials awsCredentials = new BasicAWSCredentials(qrtableGenericServiceConfig.getAccessKeyId(), qrtableGenericServiceConfig.getAccessKeySecret());
        return AmazonS3ClientBuilder.standard()
                .withRegion(qrtableGenericServiceConfig.getAwsRegion())
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
    }

    private boolean isAwsConfigured() {
        return qrtableGenericServiceConfig.getAccessKeyId() != null && !qrtableGenericServiceConfig.getAccessKeyId().isEmpty() &&
               qrtableGenericServiceConfig.getAccessKeySecret() != null && !qrtableGenericServiceConfig.getAccessKeySecret().isEmpty() &&
               qrtableGenericServiceConfig.getAwsRegion() != null && !qrtableGenericServiceConfig.getAwsRegion().isEmpty() &&
               qrtableGenericServiceConfig.getS3BucketName() != null && !qrtableGenericServiceConfig.getS3BucketName().isEmpty();
    }
}
