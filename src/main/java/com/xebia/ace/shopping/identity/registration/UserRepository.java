package com.xebia.ace.shopping.identity.registration;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    static final String EMAIL_UNIQUE_INDEX = "uk_users_email_lower";
    static final String MOBILE_UNIQUE_INDEX = "uk_users_mobile";

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByEmail(String email) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower(:email))")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public boolean existsByMobile(String mobile) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM users WHERE mobile = :mobile)")
                .param("mobile", mobile)
                .query(Boolean.class)
                .single();
    }

    public void insert(NewUser user) {
        try {
            jdbcClient.sql("""
                            INSERT INTO users (user_id, email, mobile, status, client_context)
                            VALUES (:userId, :email, :mobile, :status, :clientContext)
                            """)
                    .param("userId", user.userId())
                    .param("email", user.email())
                    .param("mobile", user.mobile())
                    .param("status", user.status())
                    .param("clientContext", user.clientContext())
                    .update();
        } catch (DuplicateKeyException e) {
            throw toDuplicateUser(e);
        }
    }

    private static RuntimeException toDuplicateUser(DuplicateKeyException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains(EMAIL_UNIQUE_INDEX)) {
            return new DuplicateUserException(RegistrationValidator.EMAIL, e);
        }
        if (detail.contains(MOBILE_UNIQUE_INDEX)) {
            return new DuplicateUserException(RegistrationValidator.MOBILE, e);
        }
        return e;
    }
}
