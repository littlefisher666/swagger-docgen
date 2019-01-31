package com.github.litttlefisher.swagger.docgen.document;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * @author littlefisher
 */
abstract class PropertyExampleMixIn {
    PropertyExampleMixIn() { }

    @JsonRawValue
    abstract Object getExample();
}
