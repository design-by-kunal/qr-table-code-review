package com.gulfnet.shared_library.entity.quartz;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qrtz_simple_triggers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(QrtzSimpleTriggersId.class)
public class QrtzSimpleTriggers {
    
    @Id
    @Column(name = "sched_name", length = 120)
    private String schedName;
    
    @Id
    @Column(name = "trigger_name", length = 200)
    private String triggerName;
    
    @Id
    @Column(name = "trigger_group", length = 200)
    private String triggerGroup;
    
    @Column(name = "repeat_count", nullable = false)
    private Long repeatCount;
    
    @Column(name = "repeat_interval", nullable = false)
    private Long repeatInterval;
    
    @Column(name = "times_triggered", nullable = false)
    private Long timesTriggered;
}