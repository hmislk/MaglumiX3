package org.carecode.mw.lims.mw.indiko;

/**
 *
 * @author Dr M H B Ariyaratne - buddhika.ari@gmail.com
 */
public class LimsSettings {

    private String limsServerBaseUrl;
    private String username;
    private String password;

    public String getLimsServerBaseUrl() {
        return limsServerBaseUrl;
    }

    public void setLimsServerBaseUrl(String limsServerBaseUrl) {
        this.limsServerBaseUrl = limsServerBaseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
