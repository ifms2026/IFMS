package com.mkwang.backend.modules.config.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * SystemConfig entity - Stores dynamic system configuration.
 * Key-value pairs for settings like approval limits, retry counts, etc.
 */
@Entity
@Table(name = "system_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig extends BaseEntity {

  @Id
  @Column(name = "config_key", length = 100)
  private String key;

  @Column(name = "config_value", nullable = false)
  private String value;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /**
   * Get value as Integer
   */
  public Integer getValueAsInt() {
    return Integer.parseInt(value);
  }

  /**
   * Get value as Long
   */
  public Long getValueAsLong() {
    return Long.parseLong(value);
  }

  /**
   * Get value as Boolean
   */
  public Boolean getValueAsBoolean() {
    return Boolean.parseBoolean(value);
  }

  /**
   * Get value as BigDecimal
   */
  public java.math.BigDecimal getValueAsBigDecimal() {
    return new java.math.BigDecimal(value);
  }
}
