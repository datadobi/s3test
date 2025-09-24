package com.datadobi.s3test.s3;

import software.amazon.awssdk.core.SelectedAuthScheme;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkInternalExecutionAttribute;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.spi.scheme.AuthSchemeOption;
import software.amazon.awssdk.identity.spi.Identity;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class NoChunkedForEmptyPutInterceptor implements ExecutionInterceptor {
    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        if (context.request() instanceof PutObjectRequest put) {
            Long contentLength = put.contentLength();
            if (contentLength != null && contentLength == 0L) {
                SelectedAuthScheme<?> authScheme = executionAttributes.getAttribute(SdkInternalExecutionAttribute.SELECTED_AUTH_SCHEME);
                if (authScheme != null) {
                    AuthSchemeOption authSchemeOption = authScheme.authSchemeOption();
                    Boolean chunkedEnabled = authSchemeOption.signerProperty(AwsV4FamilyHttpSigner.CHUNK_ENCODING_ENABLED);
                    if (Boolean.TRUE.equals(chunkedEnabled)) {
                        executionAttributes.putAttribute(
                                SdkInternalExecutionAttribute.SELECTED_AUTH_SCHEME,
                                disableChunkedEncoding(authScheme)
                        );
                    }
                }
            }
        }
    }

    private static <T extends Identity> SelectedAuthScheme<T> disableChunkedEncoding(SelectedAuthScheme<T> authScheme) {
        return new SelectedAuthScheme<T>(
                authScheme.identity(),
                authScheme.signer(),
                authScheme.authSchemeOption()
                        .toBuilder()
                        .putSignerProperty(
                                AwsV4FamilyHttpSigner.CHUNK_ENCODING_ENABLED,
                                Boolean.FALSE
                        )
                        .build()
        );
    }
}
