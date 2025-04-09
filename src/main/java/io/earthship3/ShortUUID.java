package io.earthship3;

import java.security.SecureRandom;

public class ShortUUID {
  private static final SecureRandom random = new SecureRandom();
  private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  /**
   * Generates a random UUID in the format: {5chars}-{5chars}-{5chars} (17 characters total)
   *
   * @return A random UUID string
   */
  public static String randomUUID() {
    return "%s-%s-%s".formatted(randomUUID(5), randomUUID(5), randomUUID(5));
  }

  /**
   * Generates a random UUID of the specified length
   *
   * @param length The desired length of the UUID
   * @return A random UUID string
   */
  public static String randomUUID(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("Length must be positive");
    }

    return random.ints(length, 0, ALPHABET.length())
        .mapToObj(ALPHABET::charAt)
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
        .toString();
  }
}
