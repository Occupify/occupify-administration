-- =============================================================================
-- Migration: V3__seed_crawled_employers.sql
-- Description: Seed 20 synthetic employers replica for administration service
-- Service: occupify-administration
-- =============================================================================

INSERT INTO users (id, email, full_name, avatar_url, role, status, created_at)
VALUES
    ('0dd60f9a-989f-5c7c-880e-027145375956', 'talent@nexusfintech.example', 'Nexus Fintech Solutions', 'https://api.dicebear.com/7.x/identicon/svg?seed=nexus-fintech', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('dd243d69-c690-5823-b1ef-2ee5a28d939a', 'talent@auraretail.example', 'Aura Retail & E-Commerce Labs', 'https://api.dicebear.com/7.x/identicon/svg?seed=aura-retail', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('1fd3acb5-4a53-5030-a57f-28a8c99f0780', 'talent@biopulsehealth.example', 'BioPulse HealthTech Innovations', 'https://api.dicebear.com/7.x/identicon/svg?seed=biopulse-health', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('26416f7c-3cb8-5777-a262-570434a6a1fd', 'talent@kinetixlogistics.example', 'Kinetix Logistics & Supply Chain', 'https://api.dicebear.com/7.x/identicon/svg?seed=kinetix-logistics', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('2cb5d108-bf08-58ae-adab-e6586d4bc17d', 'talent@vortexcloud.example', 'Vortex Cloud Systems', 'https://api.dicebear.com/7.x/identicon/svg?seed=vortex-cloud', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('6b4435ea-7f11-5ecf-89d6-2753f4bc1d03', 'talent@cognitiveai.example', 'CognitiveAI Ventures', 'https://api.dicebear.com/7.x/identicon/svg?seed=cognitive-ai', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('34e10d55-41d1-5d82-b53d-b4cadd98bdc5', 'talent@omnisecure.example', 'OmniSecure Cyber Defense', 'https://api.dicebear.com/7.x/identicon/svg?seed=omnisecure-cyber', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('bec1a191-1ce8-550f-81bc-58272c6ffab8', 'talent@apexmobile.example', 'Apex Mobile & Interactive Studios', 'https://api.dicebear.com/7.x/identicon/svg?seed=apex-mobile', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('bc01488d-053a-5669-881f-abbdab1c2665', 'talent@byteforge.example', 'ByteForge Enterprise Platforms', 'https://api.dicebear.com/7.x/identicon/svg?seed=byteforge-enterprise', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('16a270bc-bdd3-50d4-8d2c-2d5460da9eda', 'talent@catalystsaas.example', 'Catalyst SaaS Software', 'https://api.dicebear.com/7.x/identicon/svg?seed=catalyst-saas', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('0bc1726d-ff12-5cbc-930b-941bc096d9f9', 'talent@smartgrid.example', 'SmartGrid Energy & IoT Systems', 'https://api.dicebear.com/7.x/identicon/svg?seed=smartgrid-iot', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('8ef786b7-101d-566b-a79a-114e10f4b53d', 'talent@datasphere.example', 'DataSphere Analytics Group', 'https://api.dicebear.com/7.x/identicon/svg?seed=datasphere-analytics', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('52765afc-591b-5bcd-a2c5-bacf381d6ad5', 'talent@blockwave.example', 'BlockWave Decentralized Labs', 'https://api.dicebear.com/7.x/identicon/svg?seed=blockwave-labs', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('a3084638-5973-5ad0-8d4f-1adfd702c9cb', 'talent@pixelcraft.example', 'PixelCraft Digital Agency', 'https://api.dicebear.com/7.x/identicon/svg?seed=pixelcraft-agency', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('73ea0a01-2b08-519f-a4f5-a9bc8eb29574', 'talent@eduverse.example', 'EduVerse Learning Technologies', 'https://api.dicebear.com/7.x/identicon/svg?seed=eduverse-tech', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('20f40f6d-e4ca-5f37-a134-76f11b0e2e86', 'talent@autoflow.example', 'AutoFlow Automation Group', 'https://api.dicebear.com/7.x/identicon/svg?seed=autoflow-rpa', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('c1bf75f5-96ec-5e7a-b6d0-1848091c1c97', 'talent@quantumqa.example', 'Quantum Quality Assurance Labs', 'https://api.dicebear.com/7.x/identicon/svg?seed=quantum-qa', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('bdf8a4e1-fc13-5edf-8142-357e9c5b3912', 'talent@zenithmedia.example', 'Zenith Media & Streaming Tech', 'https://api.dicebear.com/7.x/identicon/svg?seed=zenith-media', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('a3cb3a1d-ae5d-5288-b84f-98a54dcf4a71', 'talent@proptechnexus.example', 'PropTech Nexus Real Estate', 'https://api.dicebear.com/7.x/identicon/svg?seed=proptech-nexus', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00'),
    ('8bb1d650-3af7-5889-a825-e87c69a4ed5b', 'talent@terratech.example', 'TerraTech AgriTech Innovations', 'https://api.dicebear.com/7.x/identicon/svg?seed=terratech-agri', 'USER', 'ACTIVE', '2026-10-27 00:00:00+00')
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    avatar_url = EXCLUDED.avatar_url,
    status = EXCLUDED.status;
