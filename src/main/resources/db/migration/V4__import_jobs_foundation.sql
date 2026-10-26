-- Stagehand also reads who publishes the page: the admin sees each imported foundation with its name and source page
alter table import_jobs
  add column foundation_name text,
  add column source_url text;
