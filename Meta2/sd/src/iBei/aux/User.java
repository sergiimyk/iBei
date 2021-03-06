package iBei.aux;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class User implements Serializable{
    public String username;
    public String password;
    public boolean banned;
    public List<String> notifications;
    public int leiloes;
    public int vitorias;
    public boolean logado;
    public User(String username, String password){
        this.username = username;
        this.password = password;
        this.notifications = Collections.synchronizedList(new ArrayList<String>());
        this.leiloes = 0;
        this.vitorias = 0;
        this.banned =false;
        this.logado = false;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Boolean isBanned() {
        return banned;
    }

    public int getLeiloes() {
        return leiloes;
    }

    public int getVitorias() {
        return vitorias;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setBanned(Boolean banned) {
        this.banned = banned;
    }

    public void setLeiloes(int n) {
        this.leiloes = n;
    }

    public void setvitorias(int vitorias) {
        this.vitorias = vitorias;
    }

    public void addNotification(String text){
        this.notifications.add(text);
    }

    public List<String> getNotifications() {
        return notifications;
    }

    public static int compareLeiloes(User a, User b){
        return Integer.compare(a.getLeiloes(),b.getLeiloes());
    }

    public static int compareVitorias(User a, User b){
        return Integer.compare(a.getVitorias(),b.getVitorias());
    }
}
