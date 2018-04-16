package com.axelor.apps.contract.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.ContractLine;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class ContractLineServiceImpl implements ContractLineService {

    protected AppBaseService appBaseService;
    protected AccountManagementService accountManagementService;
    protected CurrencyService currencyService;

    @Inject
    public ContractLineServiceImpl(AppBaseService appBaseService,
                                   AccountManagementService accountManagementService,
                                   CurrencyService currencyService) {
        this.appBaseService = appBaseService;
        this.accountManagementService = accountManagementService;
        this.currencyService = currencyService;
    }

    @Override
    public ContractLine reset(ContractLine contractLine) {
        if (contractLine == null) {
            return new ContractLine();
        }
        contractLine.setTaxLine(null);
        contractLine.setProductName(null);
        contractLine.setUnit(null);
        contractLine.setPrice(null);
        contractLine.setExTaxTotal(null);
        contractLine.setInTaxTotal(null);
        contractLine.setDescription(null);
        return contractLine;
    }

    @Override
    public ContractLine fill(ContractLine contractLine, Product product) {
        contractLine.setProductName(product.getName());
        if (product.getSalesUnit() != null) {
            contractLine.setUnit(product.getSalesUnit());
        } else {
            contractLine.setUnit(product.getUnit());
        }
        contractLine.setPrice(product.getSalePrice());
        contractLine.setDescription(product.getDescription());
        return contractLine;
    }

    @Override
    public ContractLine compute(ContractLine contractLine, Contract contract,
                                   Product product) throws AxelorException {
        Preconditions.checkNotNull(contract, I18n.get("Contract can't be " +
                "empty for compute contract line price."));
        Preconditions.checkNotNull(product, "Product can't be " +
                "empty for compute contract line price.");

        // TODO: maybe put tax computing in another method
        TaxLine taxLine = accountManagementService.getTaxLine(
                appBaseService.getTodayDate(), product, contract.getCompany(),
                contractLine.getFiscalPosition(), false);
        contractLine.setTaxLine(taxLine);

        if(taxLine != null && product.getInAti()) {
            BigDecimal price = contractLine.getPrice();
            price = price.divide(taxLine.getValue().add(BigDecimal.ONE), 2,
                    BigDecimal.ROUND_HALF_UP);
            contractLine.setPrice(price);
        }

        BigDecimal price = contractLine.getPrice();
        BigDecimal convert = currencyService
                .getCurrencyConversionRate(product.getSaleCurrency(),
                        contract.getCurrency(), appBaseService.getTodayDate());
        contractLine.setPrice(price.multiply(convert));

        return contractLine;
    }

    @Override
    public ContractLine fillAndCompute(ContractLine contractLine, Contract contract, Product product) throws AxelorException {
        contractLine = fill(contractLine, product);
        contractLine = compute(contractLine, contract, product);
        return contractLine;
    }

    @Override
    public ContractLine computeTotal(ContractLine contractLine, Product product) {
        BigDecimal taxRate = BigDecimal.ZERO;

        if(contractLine.getTaxLine() != null) {
            taxRate = contractLine.getTaxLine().getValue();
        }

        BigDecimal exTaxTotal = contractLine.getQty()
                .multiply(contractLine.getPrice())
                .setScale(2, RoundingMode.HALF_EVEN);
        contractLine.setExTaxTotal(exTaxTotal);
        BigDecimal inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
        contractLine.setInTaxTotal(inTaxTotal);

        return contractLine;
    }

}
