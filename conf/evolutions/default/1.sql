-- !Ups

create table events
(
    event_id int auto_increment
        primary key,
    event_name varchar(100) not null,
    event_location varchar(100) null,
    event_visibility set('Draft', 'Internal', 'Public', 'Archived') default 'Draft' not null,
    event_start datetime not null,
    event_end datetime not null,
    event_archive datetime not null,
    event_is_test tinyint(1) default 0 null,
    event_is_mainstream tinyint(1) default 1 not null
);

create table event_attributes
(
    event_id int not null,
    event_attribute_owner int not null,
    event_attribute_key varchar(100) not null,
    event_attribute_value text not null,
    primary key (event_id, event_attribute_key),
    constraint event_attributes_events_event_id_fk
        foreign key (event_id) references events (event_id)
);

-- !Downs