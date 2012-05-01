package web.mvc;

import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.view.jasperreports.AbstractJasperReportsView;
import org.springframework.web.servlet.view.jasperreports.JasperReportsMultiFormatView;
import org.springframework.web.servlet.view.jasperreports.JasperReportsPdfView;

import ro.ubb.StockAdapter.dto.ProductDTO;
import ro.ubb.StockAdapter.gateway.StockGateway;
import ro.ubb.StockAdapter.gateway.exceptions.StockGatewayException;

import domain.Oferta;
import domain.Product;

import adrian.comenziadapter.OrderGateway;

import web.converter.Converter;
import web.entity.Userrr;
import web.integration.IntegrationConstants;
import web.integration.OfertaIntegrationService;
import web.integration.ProductIntegration;
import web.integration.ProductIntegrationService;
import web.mvc.model.Bascket;
import web.mvc.model.HistoryOrder;
import web.reports.ReportService;
import web.security.SecurityUtils;

@Controller
@RequestMapping("/sales")
public class SalesController {
	
	@Autowired
	SecurityUtils userDao;
	
	@Autowired
	@Qualifier("orderGateway")
	OrderGateway ofertaService;
	
	@Autowired
	StockGateway stock;
	
	@Autowired
	OfertaIntegrationService integrationService;
	@Autowired
	ProductIntegrationService pintegrationService;
	
	@Autowired
	Bascket bascket;
	
	@RequestMapping(method = RequestMethod.GET)
	public String getSalesForRegion(Principal principal, ModelMap model) {
		
		Userrr user = userDao.getUser(principal.getName());
		Collection<HistoryOrder> oferteRegionale = new ArrayList<HistoryOrder>();
		for (String r : user.getRegions()) {
			oferteRegionale.addAll(Converter.toHistoryOrders(ofertaService.getByRegion(r)));
		}
		for(HistoryOrder ho : oferteRegionale) {
			ho.setUpdateStatus(integrationService.getStatus(ho.getOrder().getId()));
		}
		model.put("orders", oferteRegionale);
		
		return "sales/orders";
		
	}
	
	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	public String showSale(@PathVariable("id") Long id, ModelMap model,
			@RequestParam(value="upd", required = false, defaultValue = " ") String updateStatus) {
		Oferta oferta = ofertaService.getById(id);
		HistoryOrder order = new HistoryOrder();
		order.setOrder(oferta);
		order.setUpdateStatus(integrationService.getStatus(id));
		if (!"n".equals(updateStatus)) {
			integrationService.updateStatus(id, IntegrationConstants.NOT_UPDATED);
		}
		model.addAttribute("order", order);
		return "sales/order";
	}
	
	@RequestMapping(value = "/{id}/update", method = RequestMethod.POST)
	public String updateSale(@PathVariable("id") Long id, @ModelAttribute("order") HistoryOrder order
			) {
		Oferta oferta = order.getOrder();
		for (domain.Product p : oferta.getItems() ) {
			ofertaService.updatePriceProdus(id, p.getId(), p.getFinalPrice());
			
		}
		integrationService.updateStatus(id, IntegrationConstants.PRICE_UPDATED);
		return "redirect:/sales/" + id +  "?upd=n";
	}
	
	@RequestMapping(value = "/{id}/finish", method = RequestMethod.GET)
	public String finishOrder(@PathVariable("id") Long id, ModelMap model) {
		ofertaService.oferteaza(id);
		model.put("orderId", id);
		return "sales/orders";
	}
	
	@RequestMapping(value = "/{id}/process", method = RequestMethod.GET)
	public ModelAndView processOrder(@PathVariable("id") Long id) {
		ModelAndView mv = new ModelAndView();
		Oferta processed = null;
		try {
			
			processed = ofertaService.getById(id);
			for (domain.Product p : processed.getItems()) {
				ProductIntegration pi = pintegrationService.getStockId(p.getId());
				ProductDTO product = stock.getProduct(pi.getStockId(), pi.getCategoryId());
				updateProductStock(product, p);
				bascket.getCategoryProducts().remove(pi.getCategoryId());
			}
			ofertaService.proceseazaComanda(id);
			
		} catch (StockGatewayException e) {
			e.printStackTrace();
			//go back to new orders
			View rv = new RedirectView("/web-app/product/ordersn", false);
			mv.setView(rv);
		}
		//go to PDF
		ReportService reportsService = new ReportService();
		Map model = reportsService.getOfertaParameters(processed);
		return new ModelAndView("pdfComanda", model); 
	}

	private void updateProductStock(ProductDTO product, Product p) throws StockGatewayException {
		if (product.stock >= p.getQuantity()) {
			product.stock = product.stock - p.getQuantity();
		} else {
			product.stock = 0;
		}
		stock.updateProduct(product);
		
	}


}
