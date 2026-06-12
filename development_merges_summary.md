# Development Branch Merges into Staging

## Summary of Changes Merged from Development to Staging

### Latest Merges (Updated: Mar 13, 2026)

#### 1. **PR #1295** - c22e1b9bd (Mar 13, 2026 - Latest)
- **Author:** Kunal Rathod
- **Description:** GetOrderFixess
- **Files Changed:** 4 files, 72 insertions(+), 21 deletions(-)
- **Key Changes:**
  - OrderServiceImpl.java (optimized fetching)
  - RestaurantLayoutServiceImpl.java
  - OrderRepository.java (query optimization)
  - OrderedItemModifierRepository.java (batch fetching)

#### 2. **PR #1294** - 9b8595752 (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** GetOrderFixess
- **Files Changed:** (merged into PR #1295)

#### 3. **PR #1293** - 4fa7fb0f0 (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** TableLayoutFixes
- **Key Changes:**
  - RestaurantLayoutServiceImpl.java (transient sections/rows persistence)

#### 4. **PR #1292** - b968d5fde (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** GetOrderApiFixes
- **Files Changed:** 4 files, 32 insertions(+), 8 deletions(-)
- **Key Changes:**
  - OrderServiceImpl.java
  - OrderRepository.java
  - OrderedComboRepository.java
  - messages_ja.properties

#### 5. **PR #1291** - fbb7330ae (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** GetOrderApiFixes
- **Key Changes:**
  - OrderServiceImpl.java (fetch ordered combos separately)
  - OrderRepository.java
  - OrderedComboRepository.java

#### 6. **PR #1290** - f0fa627a1 (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** Added Missing Messages
- **Files Changed:** 7 files, 376 insertions(+), 10 deletions(-)
- **Key Changes:**
  - ReceiptService.java
  - RefundReceiptService.java
  - BulkItemUploadServiceImpl.java
  - messages_ja.properties (179 new messages)
  - messages_th.properties (56 new messages)

#### 7. **PR #1289** - 4effddc3a (Mar 13, 2026)
- **Author:** Nitesh Kumar Singh
- **Description:** QTOS_2870
- **Key Changes:** (merged from QTOS_2870 branch)

#### 8. **PR #1288** - 95034e5d5 (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** Query Optimization
- **Files Changed:** 5 files, 674 insertions(+), 189 deletions(-)
- **Key Changes:**
  - OrderNotificationServiceImpl.java (major refactor)
  - OrderPricingServiceImpl.java (255 lines added)
  - OrderServiceImpl.java (125 lines modified)
  - RestaurantLayoutServiceImpl.java (160 lines modified)
  - OrderRepository.java (155 new lines)

#### 9. **PR #1287** - f87fb5293 (Mar 13, 2026)
- **Author:** Kunal Rathod
- **Description:** QueryOptimization
- **Key Changes:**
  - Order processing optimization
  - Database interaction improvements
  - N+1 query prevention

#### 10. **PR #1286** - 12cbf701b (Mar 13, 2026)
- **Author:** shivani9634
- **Description:** changes for notification type filter
- **Files Changed:** 2 files, 47 insertions(+), 6 deletions(-)
- **Key Changes:**
  - NotificationController.java
  - NotificationQueryService.java

### Recent Development Merges (Mar 12-13, 2026)

#### **PR #1284** - af9ae7fd9 (Mar 13, 2026)
- **Author:** Nitesh Kumar Singh
- **Description:** (Merge from development)

#### **PR #1283** - a7a6e9995 (Mar 13, 2026)
- **Author:** Nitesh Kumar Singh
- **Description:** FONT-JA-ADDED
- **Key Changes:** Japanese receipt fonts

#### **PR #1282** - 7251fd398 (Mar 12, 2026)
- **Author:** shivani9634
- **Description:** (Merge from development)

#### **PR #1280** - 8ef7de69b (Mar 12, 2026)
- **Author:** Kunal Rathod
- **Description:** make item status sync
- **Files Changed:** 2 files, 65 insertions(+), 67 deletions(-)
- **Key Changes:**
  - OrderNotificationService.java
  - OrderNotificationServiceImpl.java (refactored async to sync)

#### **PR #1278** - df295144b (Mar 12, 2026)
- **Author:** Kunal Rathod
- **Description:** Added Indexes in DB
- **Files Changed:** 3 files, 279 insertions(+)
- **Key Changes:**
  - Added performance optimization indexes
  - Database migration files

### Additional Merges (Older)

- **PR #1255** - 7ef792c1 (Mar 6, 2026) - 13 files changed
- **PR #1251** - 4b6f3489 (Mar 6, 2026) - 9 files changed
- **PR #1247** - 716ff7bf (Mar 5, 2026) - 10 files changed
- **PR #1244** - 25784053 (Mar 4, 2026) - 17 files changed, 1133 insertions(+), 791 deletions(-)
- **PR #1243** - 9c5d6c62 (Mar 4, 2026) - 1 file changed
- **PR #1241** - b74c322f (Mar 4, 2026) - 12 files changed
- **PR #1238** - b8468ea9 (Mar 3, 2026) - 100 files changed, 1486 insertions(+), 834 deletions(-)

### Total Development Merges Found
- **40+ merges** from development branch in the last 2 weeks
- Most active contributors: Kunal Rathod, Nitesh Kumar Singh, shivani9634
- **Latest merge:** PR #1295 (Mar 13, 2026)

### Summary of Latest Activity (Mar 13, 2026)
- **10+ merges** today alone
- Major focus areas:
  - Query optimization and performance improvements
  - Order API fixes and optimizations
  - Missing message translations (Japanese & Thai)
  - Notification filtering enhancements
  - Table layout fixes

### Previously Pending (Now Merged ✅)
- ✅ PR #1293 - TableLayoutFixes (Merged)
- ✅ PR #1291 - GetOrderApiFixes (Merged)
- ✅ PR #1289 - QTOS_2870 (Merged)
- ✅ PR #1287 - QueryOptimization (Merged)
- ✅ PR #1285 - QTOS-8080 (Merged)
- ✅ PR #1283 - FONT-JA-ADDED (Merged)
