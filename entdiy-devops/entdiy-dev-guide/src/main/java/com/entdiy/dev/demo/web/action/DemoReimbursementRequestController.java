/**
 * Copyright © 2015 - 2017 EntDIY JavaEE Development Framework
 *
 * Site: https://www.entdiy.com, E-Mail: xautlx@hotmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.entdiy.dev.demo.web.action;

import com.entdiy.auth.entity.Account;
import com.entdiy.auth.service.UserService;
import com.entdiy.core.annotation.MenuData;
import com.entdiy.core.annotation.MetaData;
import com.entdiy.core.pagination.GroupPropertyFilter;
import com.entdiy.core.util.DateUtils;
import com.entdiy.core.util.JsonUtils;
import com.entdiy.core.web.BaseController;
import com.entdiy.core.web.annotation.ModelEntity;
import com.entdiy.core.web.annotation.ModelPageableRequest;
import com.entdiy.core.web.annotation.ModelPropertyFilter;
import com.entdiy.core.web.json.JsonViews;
import com.entdiy.core.web.util.ServletUtils;
import com.entdiy.core.web.view.OperationResult;
import com.entdiy.dev.demo.entity.DemoReimbursementRequest;
import com.entdiy.dev.demo.entity.DemoReimbursementRequestItem;
import com.entdiy.dev.demo.service.DemoReimbursementRequestService;
import com.entdiy.dev.demo.support.DemoConstant;
import com.entdiy.security.annotation.AuthAccount;
import com.entdiy.sys.service.AttachmentFileService;
import com.entdiy.sys.service.DataDictService;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;

@MetaData("报销申请管理")
@Controller
@RequestMapping(value = "/dev/demo/reimbursement-request")
public class DemoReimbursementRequestController extends BaseController<DemoReimbursementRequest, Long> {

    @Autowired
    private UserService userService;

    @Autowired
    private DemoReimbursementRequestService reimbursementRequestService;

    @Autowired
    private DataDictService dataDictService;

    @Autowired
    private AttachmentFileService attachmentFileService;

    @MenuData("演示样例:报销申请")
    @RequiresPermissions("演示样例:报销申请")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public String index(@ModelEntity DemoReimbursementRequest entity, Model model) {
        model.addAttribute("useTypeJson",
                JsonUtils.writeValueAsString(dataDictService.findMapDataByRootPrimaryKey(DemoConstant.DataDict_Demo_ReimbursementRequest_UseType)));
        return "dev/demo/reimbursementRequest-index";
    }

    @RequiresPermissions("演示样例:报销申请")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    @JsonView(JsonViews.Admin.class)
    public Page<DemoReimbursementRequest> findByPage(@ModelPropertyFilter(DemoReimbursementRequest.class) GroupPropertyFilter filter,
                                                     @ModelPageableRequest Pageable pageable) {
        return reimbursementRequestService.findByPage(filter, pageable);
    }

    @RequestMapping(value = "/edit-tabs", method = RequestMethod.GET)
    public String editTabs(@ModelEntity DemoReimbursementRequest entity, HttpServletRequest request) {
        return "dev/demo/reimbursementRequest-inputTabs";
    }

    @RequiresPermissions("演示样例:报销申请")
    @RequestMapping(value = "/edit", method = RequestMethod.GET)
    public String editShow(@AuthAccount Account account, @ModelEntity(preFectchLazyFields = {"reimbursementRequestItems"}) DemoReimbursementRequest entity,
                           Model model) {
        model.addAttribute("subValidationRules", ServletUtils.buildValidateRules(DemoReimbursementRequestItem.class));

        if (entity.isNew()) {
            //默认取当前登录用户所属部门，用户可编辑修改
            entity.setDepartment(userService.findByAccount(account).getDepartment());
        }

        //模板记录初始化及属性设置
        DemoReimbursementRequestItem newItemTemplate = new DemoReimbursementRequestItem();
        newItemTemplate.setStartDate(DateUtils.currentDateTime().toLocalDate());
        //将追加模板记录添加到集合用于前端循环显示
        List<DemoReimbursementRequestItem> reimbursementRequestItems = entity.getReimbursementRequestItems();
        if (CollectionUtils.isEmpty(reimbursementRequestItems)) {
            reimbursementRequestItems = Lists.newArrayList();
            entity.setReimbursementRequestItems(reimbursementRequestItems);
        }
        reimbursementRequestItems.add(newItemTemplate);

        attachmentFileService.injectAttachmentFilesToEntity(entity, "receiptAttachmentFiles");

        model.addAttribute("useTypeMap",
                dataDictService.findMapDataByRootPrimaryKey(DemoConstant.DataDict_Demo_ReimbursementRequest_UseType));

        return "dev/demo/reimbursementRequest-inputBasic";
    }

    @RequiresPermissions("演示样例:报销申请")
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    @ResponseBody
    public OperationResult editSave(@AuthAccount Account account,
                                    @ModelEntity(preFectchLazyFields = {"reimbursementRequestItems"}) DemoReimbursementRequest entity) {
        //动态行项数据
        List<DemoReimbursementRequestItem> items = entity.getReimbursementRequestItems();

        if (entity.isNew()) {
            entity.setUser(userService.findByAccount(account));
        } else {
            //明细行项关联数据处理
            items.removeIf(item -> {
                if (item.getId() == null) {
                    //新增对象设置当前主对象关联
                    item.setReimbursementRequest(entity);
                } else if (item.getId() < 0) {
                    //如果id为负值标识为待删除元素，并且重置id为正值，以便hibernate后续删除元素
                    item.setId(-item.getId());
                    return true;
                }
                //新增或更新则保留元素
                return false;
            });
        }
        //汇总计算设置总金额
        entity.setTotalInvoiceAmount(items.stream().map(DemoReimbursementRequestItem::getInvoiceAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        //先调用业务接口持久化主对象
        OperationResult result = super.editSave(entity);

        //附件处理（考虑到附件就是简单的字段更新基本不会出现业务失败，即便异常也不会对主业务逻辑带来严重问题，因此放在另外事务中调用）
        attachmentFileService.saveBySource(entity, "receiptAttachmentFiles");

        return result;
    }

    @RequiresPermissions("演示样例:报销申请")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    @Override
    public OperationResult delete(@RequestParam("ids") Long... ids) {
        return super.delete(ids);
    }
}