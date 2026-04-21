package com.example.CWMS.service;

import com.example.CWMS.iservice.SiteService;
import com.example.CWMS.model.cwms.Site;
import com.example.CWMS.repository.cwms.SiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;


@Service
public class SiteServiceImpl implements SiteService {
    @Autowired
    private SiteRepository siteRepository;

    @Override
    public List<Site> getAllSites() {
        return siteRepository.findAll();
    }

    @Override
    public Site getSiteById(Integer id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Site non trouvé avec l'id : " + id));
    }

    @Override
    public Site createSite(Site site) {
        site.setCreatedAt(new Date());
        return siteRepository.save(site);
    }

    @Override
    public Site updateSite(Integer id, Site siteDetails) {
        Site site = getSiteById(id);
        site.setSiteName(siteDetails.getSiteName());
        site.setUpdatedAt(new Date());
        return siteRepository.save(site);
    }

    @Override
    public void deleteSite(Integer id) {
        Site site = getSiteById(id);
        siteRepository.delete(site);
    }
}