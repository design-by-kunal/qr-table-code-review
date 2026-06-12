package com.gulfnet.shared_library.entity.quartz;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qrtz_job_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(QrtzJobDetailsId.class)
public class QrtzJobDetails {
    
    @Id
    @Column(name = "sched_name", length = 120)
    private String schedName;
    
    @Id
    @Column(name = "job_name", length = 200)
    private String jobName;
    
    @Id
    @Column(name = "job_group", length = 200)
    private String jobGroup;
    
    @Column(name = "description", length = 250)
    private String description;
    
    @Column(name = "job_class_name", length = 250, nullable = false)
    private String jobClassName;
    
    @Column(name = "is_durable", nullable = false)
    private Boolean isDurable;
    
    @Column(name = "is_nonconcurrent", nullable = false)
    private Boolean isNonconcurrent;
    
    @Column(name = "is_update_data", nullable = false)
    private Boolean isUpdateData;
    
    @Column(name = "requests_recovery", nullable = false)
    private Boolean requestsRecovery;
    
    @Lob
    @Column(name = "job_data", columnDefinition = "bytea")
    private byte[] jobData;
} 