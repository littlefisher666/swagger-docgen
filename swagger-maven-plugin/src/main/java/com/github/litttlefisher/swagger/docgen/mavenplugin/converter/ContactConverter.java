package com.github.litttlefisher.swagger.docgen.mavenplugin.converter;

import com.github.litttlefisher.swagger.docgen.mavenplugin.properties.ContactProperty;

import io.swagger.models.Contact;

/**
 * @author jinyn22648
 * @version $$Id: ContactConverter.java, v 0.1 2019/1/31 2:11 PM jinyn22648 Exp $$
 */
public class ContactConverter {

    private ContactConverter() {
    }

    public static Contact convert(ContactProperty property) {
        if (property == null) {
            return null;
        } else {
            Contact contact = new Contact();
            contact.setUrl(property.getUrl());
            contact.setName(property.getName());
            contact.setEmail(property.getEmail());
            return contact;
        }
    }
}
