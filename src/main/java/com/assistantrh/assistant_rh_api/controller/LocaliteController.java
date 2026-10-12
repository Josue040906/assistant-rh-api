package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Localite;
import com.assistantrh.assistant_rh_api.service.LocaliteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/localites")
public class LocaliteController {

    private final LocaliteService localiteService;

    public LocaliteController(LocaliteService localiteService) {
        this.localiteService = localiteService;
    }

    @GetMapping("/regions")
    public List<Localite> regions() {
        return localiteService.regions();
    }

    @GetMapping("/districts")
    public List<Localite> districts(@RequestParam long regionId) {
        return localiteService.districts(regionId);
    }

    @GetMapping("/communes")
    public List<Localite> communes(@RequestParam long districtId) {
        return localiteService.communes(districtId);
    }

    @GetMapping("/quartiers")
    public List<Localite> quartiers(@RequestParam long communeId) {
        return localiteService.quartiers(communeId);
    }

    @GetMapping("/recherche")
    public LocaliteService.PageResultat rechercher(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return localiteService.recherche(q, page, size);
    }

    @GetMapping("/{id:\\d+}")
    public LocaliteService.LocaliteDetail obtenir(@PathVariable long id) {
        return localiteService.detail(id);
    }
}
