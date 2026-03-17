package com.liquidation.riskengine.infra.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping({"/dashboard", "/dashboard/"})
    public String dashboard() {
        return "forward:/dashboard/index.html";
    }

    @GetMapping({"/dashboard/admin/feedback", "/dashboard/admin/feedback/"})
    public String feedbackAdmin() {
        return "forward:/dashboard/admin-feedback.html";
    }
}
