package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.GmoLinkPlusProperties;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusAlterTranService;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusSearchTradeService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoLinkPlusAlterTranServiceImpl implements GmoLinkPlusAlterTranService {

    private static final String JOB_CD_RETURN = "RETURN";

    private final GmoLinkPlusProperties gmoLinkPlusProperties;
    private final GmoLinkPlusSearchTradeService gmoLinkPlusSearchTradeService;
    private final MessageUtil messageUtil;

    @Qualifier("gmoRestTemplate")
    private final RestTemplate gmoRestTemplate;

    @Override
    public void submitCardReturn(Transaction transaction, BigDecimal amount, Locale locale) {
        if (!gmoLinkPlusProperties.isAlterTranConfigured()) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    messageUtil.getMessage("refund.card.gmo.not.configured", locale));
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST,
                    messageUtil.getMessage("refund.amount.invalid", locale));
        }

        String accessId = transaction.getGmoAccessId();
        String accessPass = transaction.getGmoAccessPass();
        if (needsCredentialLookup(accessId, accessPass)) {
            String gmoOrderId = resolveGmoOrderId(transaction);
            if (gmoOrderId == null) {
                throw new ResponseStatusException(BAD_REQUEST,
                        messageUtil.getMessage("refund.card.missing.credentials", locale));
            }
            Optional<GmoLinkPlusSearchTradeService.GmoLinkPlusTradeLookup> lookup =
                    gmoLinkPlusSearchTradeService.searchTradeByOrderId(gmoOrderId);
            if (lookup.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST,
                        messageUtil.getMessage("refund.card.missing.credentials", locale));
            }
            accessId = lookup.get().accessId();
            accessPass = lookup.get().accessPass();
        }

        long amountInt = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ShopID", gmoLinkPlusProperties.getShopId().trim());
        form.add("ShopPass", gmoLinkPlusProperties.getShopPass().trim());
        form.add("AccessID", accessId.trim());
        form.add("AccessPass", accessPass.trim());
        form.add("JobCd", JOB_CD_RETURN);
        form.add("Amount", String.valueOf(amountInt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String url = gmoLinkPlusProperties.resolveAlterTranUrl();
        log.info("[GMO LinkPlus AlterTran] RETURN txId={} AccessID={} Amount={}",
                transaction.getId(), accessId, amountInt);

        try {
            ResponseEntity<String> response = gmoRestTemplate.postForEntity(
                    url, new HttpEntity<>(form, headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY,
                        messageUtil.getMessage("refund.card.gmo.empty.response", locale));
            }
            Map<String, String> fields = GmoLinkPlusSearchTradeServiceImpl.parseUrlEncodedBody(body);
            String errCode = firstField(fields, "ErrCode");
            if (errCode != null && !errCode.isBlank()) {
                log.warn("[GMO LinkPlus AlterTran] ErrCode={} ErrInfo={} txId={}",
                        errCode, firstField(fields, "ErrInfo"), transaction.getId());
                throw new ResponseStatusException(BAD_REQUEST,
                        messageUtil.getMessage("refund.card.gmo.rejected", locale)
                                + " ErrCode=" + errCode);
            }
            log.info("[GMO LinkPlus AlterTran] RETURN accepted by GMO for txId={} (awaiting result notification)",
                    transaction.getId());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GMO LinkPlus AlterTran] Request failed for txId={}", transaction.getId(), e);
            throw new ResponseStatusException(BAD_GATEWAY,
                    messageUtil.getMessage("refund.card.gmo.error", locale) + ": " + e.getMessage());
        }
    }

    private static boolean needsCredentialLookup(String accessId, String accessPass) {
        if (accessId == null || accessId.isBlank()) {
            return true;
        }
        return accessPass == null || accessPass.isBlank()
                || GmoLinkPlusSearchTradeServiceImpl.isMaskedAccessPass(accessPass);
    }

    private static String resolveGmoOrderId(Transaction transaction) {
        if (transaction.getGmoOrderId() != null && !transaction.getGmoOrderId().isBlank()) {
            return transaction.getGmoOrderId().trim();
        }
        if (transaction.getOrder() != null
                && transaction.getOrder().getGmoLinkOrderId() != null
                && !transaction.getOrder().getGmoLinkOrderId().isBlank()) {
            return transaction.getOrder().getGmoLinkOrderId().trim();
        }
        return null;
    }

    private static String firstField(Map<String, String> fields, String key) {
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
