package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.reminder")
public class ReminderProperties {

    private boolean enabled;
    private int dueSoonHours = 24;
    private long dedupeMinutes = 60;
    private String cron = "0 */30 * * * *";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDueSoonHours() {
        return dueSoonHours;
    }

    public void setDueSoonHours(int dueSoonHours) {
        this.dueSoonHours = dueSoonHours;
    }

    public long getDedupeMinutes() {
        return dedupeMinutes;
    }

    public void setDedupeMinutes(long dedupeMinutes) {
        this.dedupeMinutes = dedupeMinutes;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
