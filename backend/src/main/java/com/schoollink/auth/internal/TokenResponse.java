package com.schoollink.auth.internal;

record TokenResponse(String tokenType, String accessToken, long expiresIn, String refreshToken, UserInfo user) {
}
