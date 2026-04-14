package com.anju.appointment.common;

public final class DataMaskingUtil {

    private DataMaskingUtil() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);

        String maskedLocal = local.length() <= 3
                ? local.substring(0, 1) + "***"
                : local.substring(0, 3) + "****";

        String maskedDomain = domain.length() <= 4
                ? "***" + domain.substring(domain.length() - 1)
                : domain.substring(domain.length() - 7);

        return maskedLocal + maskedDomain;
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        int spaceIndex = name.indexOf(' ');
        if (spaceIndex > 0) {
            return name.substring(0, spaceIndex) + " ***";
        }
        if (name.length() <= 1) {
            return name;
        }
        return name.substring(0, 1) + " ***";
    }
}
