package com.kwanii.chat.server;

import com.kwanii.chat.HashHandler;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.stream.Stream;

/**
 * Database handler for login service
 * users (
 *   id varchar2(20),
 *   password BLOB,
 *   salt BLOB
 * );
 */

public class DBHandler implements HashHandler
{
    // Database server ip
    final private static String DB_SERVER_URL = "jdbc:oracle:thin:@//localhost:1521";

    // Database name
    final private static String DB_NAME = "XE";

    // Administrator id
    final private static String ADMIN_ID = "chat";

    // Administrator password
    final private static String ADMIN_PASS = "1234";

    // the iteration count for hashing
    final private static int ITERATIONS = 90000;

    // 20 bytes for salt
    final private static int SALT_SIZE = 20;

    // The number of bits to generate key 20 bytes
    final private static int KEY_SIZE = SALT_SIZE * 8;

    // Sql login format
    final private static String LOGIN_FORMAT =
        "SELECT password, salt FROM users WHERE id = ?";

    // Sql sign up format
    final private static String SIGN_UP_FORMAT =
        "INSERT INTO users (id, password, salt) VALUES (?, ?, ?)";

    // Database server connection
    private Connection connection;

    // to prepare sql login
    private PreparedStatement loginStmt;

    // to prepare sql sign up
    private PreparedStatement signUpStmt;

    /**
     * Connect to mysql database server
     *
     * @return true if connection succeeds
     */

    public boolean connectDB()
    {
        try
        {
            connection = DriverManager.getConnection(
                DB_SERVER_URL + "/" + DB_NAME, ADMIN_ID, ADMIN_PASS);

            loginStmt = connection.prepareStatement(LOGIN_FORMAT);
            signUpStmt = connection.prepareStatement(SIGN_UP_FORMAT);

            return true;
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Checks the user id exists
     *
     * @param id user id
     * @return true if the user id exists
     */

    public boolean exists(String id)
    {
        try
        {
            loginStmt.setString(1, id);

            return loginStmt.executeQuery().next();
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }

        return false;
    }

    /**
     * Check the user id and password are valid
     *
     * @param id the user id
     * @param password the password
     * @return true the id and password are valid
     */
    public boolean login(String id, String password)
    {
        try
        {
            loginStmt.setString(1, id);

            ResultSet result = loginStmt.executeQuery();

            // check the result and verify the password with salt and the hash
            if (result.next())
            {
                byte[] hash = result.getBytes("password");
                byte[] salt = result.getBytes("salt");

                return verify(password, hash, salt);
            }
            else
                return false;
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }

        return false;
    }

    /**
     * Before Sign up Check the user id and password
     * then create a hash and salt
     *
     * @param id User id
     * @param password Password
     * @return  true if sign up succeeds
     */
    public boolean signUp(String id, String password)
    {

        try
        {
            loginStmt.setString(1, id);

            // check the id exists
            if (loginStmt.executeQuery().next())
                return false;
            else
            {
                // create the salt and hash
                byte[] salt = getSalt(SALT_SIZE);
                byte[] hash = hash(password, salt);

                // send the id, hash and salt to the database
                signUpStmt.setString(1, id);
                signUpStmt.setBinaryStream(2, new ByteArrayInputStream(hash));
                signUpStmt.setBinaryStream(3, new ByteArrayInputStream(salt));

                signUpStmt.execute();
                return true;
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Create a secure random number for the salt
     *
     * @param numByte the number of bytes to get
     * @return The salt
     */
    public byte[] getSalt(int numByte)
    {
        return SecureRandom.getSeed(numByte);
    }

    /**
     * Create the hash from the password with the salt using PBKDF2 algorithm
     *
     * @param password a password to be hashed
     * @param salt a salt to use hashing the password
     * @return a hashed password
     */
    public byte[] hash(String password, byte[] salt)
    {
        char[] passwordChars = password.toCharArray();

        // setup the key spec
        PBEKeySpec spec =
            new PBEKeySpec(passwordChars, salt, ITERATIONS, KEY_SIZE);

        try
        {
            SecretKeyFactory key =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            return key.generateSecret(spec).getEncoded();
        }
        catch (NoSuchAlgorithmException|InvalidKeySpecException ex)
        {
            ex.printStackTrace();
        }

        return null;
    }

    /**
     * Verify the user password.
     * hash the password with the salt and compare each byte
     *
     * @param password To be verified
     * @param hash To be compared with the password
     * @param salt To generate the hash from the password
     * @return true if the hashed password and hash match
     */
    public boolean verify(String password, byte[] hash, byte[] salt)
    {
        // get a hash for password to verify
        byte[] hash2 = hash(password, salt);

        // check the both length
        int diff = hash.length ^ hash2.length;

        // check each byte matches
        for (int i = 0; i < hash.length; i++)
            diff |= hash[i] ^ hash2[i];

        return diff == 0;
    }
}
