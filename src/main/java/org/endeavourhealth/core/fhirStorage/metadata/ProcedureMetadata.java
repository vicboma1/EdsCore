package org.endeavourhealth.core.fhirStorage.metadata;

import org.hl7.fhir.instance.model.CodeableConcept;
import org.hl7.fhir.instance.model.Coding;
import org.hl7.fhir.instance.model.Procedure;
import org.hl7.fhir.instance.model.ResourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProcedureMetadata extends AbstractResourceMetadata implements PatientCompartment {
    private UUID patientId;
    private List<Code> codes;

    @Override
    public UUID getPatientId() {
        return patientId;
    }

    public List<Code> getCodes() { return codes; }

    public ProcedureMetadata(Procedure resource) {
        super(resource);
        populateMetadataFromResource(resource);
    }

    private void populateMetadataFromResource(Procedure resource) {
        patientId = UUID.fromString(ReferenceHelper.getReferenceId(resource.getSubject(), ResourceType.Patient));
        CodeableConcept codeableConcept = resource.getCode();
        codes = new ArrayList<>();

        for (Coding coding : codeableConcept.getCoding()) {
            String system = coding.getSystem();
            String code = coding.getCode();
            String display = coding.getDisplay();

            Code newCode = new Code();
            newCode.setSystem(system);
            newCode.setCode(code);

            codes.add(newCode);
        }

    }
}
