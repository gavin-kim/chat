package com.kwanii.chat;

public interface HashHandler
{

    /**
     * Generates a salt
     *
     * @param numByte the number of bytes to get
     * @return a byte array
     */
    byte[] getSalt(int numByte);

    /**
     * Generates a hashed password
     *
     * @param password a password to be hashed
     * @param salt a salt to use hashing the password
     * @return a hashed password
     */
    byte[] hash(String password, byte[] salt);

    /**
     * Verifies if the password matches the hash.
     *
     * @param password To be verified
     * @param hash To be compared with the password
     * @param salt To generate the hash from the password
     * @return true if the password matches hash
     */
    boolean verify(String password, byte[] hash, byte[] salt);
}
