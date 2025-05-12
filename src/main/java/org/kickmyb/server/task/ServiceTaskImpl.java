package org.kickmyb.server.task;

import org.joda.time.DateTime;
import org.kickmyb.server.account.MUser;
import org.kickmyb.server.account.MUserRepository;
import org.kickmyb.transfer.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
@Transactional
public class ServiceTaskImpl implements ServiceTask {

    @Autowired
    MUserRepository repoUser;
    @Autowired MTaskRepository repo;
    @Autowired MProgressEventRepository repoProgressEvent;

    private int percentage(Date start, Date current, Date end){
        if (current.after(end)) return 100;
        long total = end.getTime() - start.getTime();
        long spent = current.getTime() - start.getTime();
        double percentage =  100.0 * spent / total;
        // TODO si end est avant start c'est tout cassé.
        return Math.max((int) percentage, 0 );
    }

    @Override
    public TaskDetailResponse detail(Long id, MUser user) {
        //MTask element = user.tasks.stream().filter(elt -> elt.id == id).findFirst().get();
        MTask element = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (element.user.id != user.id) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to view this task");
        }

        TaskDetailResponse response = new TaskDetailResponse();
        response.name = element.name;
        response.id = element.id;
        // calcul le temps écoulé en pourcentage
        response.percentageTimeSpent = percentage(element.creationDate, new Date(), element.deadline);
        // aller chercher le dernier événement de progrès
        response.percentageDone = percentageDone(element);
        response.deadline = element.deadline;
        response.events = new ArrayList<>();
        for (MProgressEvent e : element.events) {
            ProgressEvent transfer = new ProgressEvent();
            transfer.value = e.resultPercentage;
            transfer.timestamp = e.timestamp;
            response.events.add(transfer);
        }
        return response;
    }

    // TODO oublier de valider pour une injection javascript
    // TODO Que se passe-t-il si ce n'est pas transactionnel ()
    // TODO test unicité avec script de charge
    @Override
    public void addOne(AddTaskRequest req, MUser user) throws Existing, Empty, TooShort {
        // valider que c'est non vide
        if (req.name.trim().length() == 0) throw new Empty();
        if (req.name.trim().length() < 2) throw new TooShort();
        // valider si le nom existe déjà
        for (MTask b : user.tasks) {
            if (b.name.equalsIgnoreCase(req.name)) throw new Existing();
        }
        // tout est beau, on crée
        MTask t = new MTask();
        t.name = req.name;
        t.user = user;

        t.creationDate = DateTime.now().toDate();
        if (req.deadline == null) {
            t.deadline = DateTime.now().plusDays(7).toDate();
        } else {
            t.deadline = req.deadline;
        }
        repo.save(t);
        user.tasks.add(t);
        repoUser.save(user);
    }

    @Override
    public void updateProgress(long taskID, int value) {
        // TODO validate value is between 0 and 100
        MTask element = repo.findById(taskID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        MUser current = currentUser();
        if (!element.user.id.equals(current.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to modify this task");
        }

        if (value < 0 || value > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Progress must be between 0 and 100");
        }

        MProgressEvent pe = new MProgressEvent();
        pe.resultPercentage = value;
        pe.completed = value == 100;
        pe.timestamp = DateTime.now().toDate();
        repoProgressEvent.save(pe);

        element.events.add(pe);
        repo.save(element);

    }

    @Override
    public List<HomeItemResponse> home(Long userID) {
        MUser user = repoUser.findById(userID).get();
        List<HomeItemResponse> res = new ArrayList<>();
        for (MTask t : user.tasks) {
            HomeItemResponse r = new HomeItemResponse();
            r.id = t.id;
            r.percentageDone = percentageDone(t);
            r.deadline = t.deadline;
            r.percentageTimeSpent = percentage(t.creationDate, new Date(), t.deadline);
            r.name = t.name;
            res.add(r);
        }
        return res;
    }

    private int percentageDone(MTask t) {
        return t.events.isEmpty()? 0 : t.events.get(t.events.size()-1).resultPercentage;
    }

    // TODO try to see how to make an injection attack example by directly exposing data from DB
    @Override
    public String index() {
        String res = "<html>";
        res += "<div>Index :</div>";
        for (MUser u: repoUser.findAll()) {
            res += "<div>" + u.username  ;
            for (MTask t : u.tasks) {
                res += "<div>" + t.name  + "</div>";
            }
            res += "</div>";
        }
        res += "</html>";
        return res;
    }

    @Override
    public MUser userFromUsername(String username) {
        return repoUser.findByUsername(username).get();
    }

    @Override
    public List<HomeItemPhotoResponse> homePhoto(Long userID) {
        MUser user = repoUser.findById(userID).get();
        List<HomeItemPhotoResponse> res = new ArrayList<>();
        for (MTask t : user.tasks) {
            HomeItemPhotoResponse r = new HomeItemPhotoResponse();
            r.id = t.id;
            r.percentageDone = percentageDone(t);
            r.deadline = t.deadline;
            r.percentageTimeSpent = percentage(t.creationDate, new Date(), t.deadline);
            r.name = t.name;
            if(t.photo != null) {
                r.photoId = t.photo.id;
            } else {
                r.photoId = 0L;
            }
            res.add(r);
        }
        return res;
    }

    @Override
    public TaskDetailPhotoResponse detailPhoto(Long id, MUser user) {
        MTask element = user.tasks.stream().filter(elt -> elt.id == id).findFirst().get();
        TaskDetailPhotoResponse response = new TaskDetailPhotoResponse();
        response.name = element.name;
        response.id = element.id;
        // calcul le temps écoulé en pourcentage
        response.percentageTimeSpent = percentage(element.creationDate, new Date(), element.deadline);
        // aller chercher le dernier événement de progrès
        response.percentageDone = percentageDone(element);
        response.deadline = element.deadline;
        response.events = new ArrayList<>();
        for (MProgressEvent e : element.events) {
            ProgressEvent transfer = new ProgressEvent();
            transfer.value = e.resultPercentage;
            transfer.timestamp = e.timestamp;
            response.events.add(transfer);
        }
        if(element.photo != null) {
            response.photoId = element.photo.id;
        } else {
            response.photoId = 0L;
        }

        return response;
    }

    private MUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userFromUsername(username); // méthode déjà existante dans ta classe
    }

    @Override
    public void supprimerTache(Long id, MUser user) {
        MTask task = repo.findById(id).orElseThrow();

        boolean appartient = user.tasks.stream().anyMatch(t -> t.id.equals(task.id));
        if (!appartient) {
            throw new SecurityException("Vous ne pouvez pas supprimer cette tâche.");
        }

        // 1. Supprimer la relation d'abord
        user.tasks.removeIf(t -> t.id.equals(task.id));
        repoUser.saveAndFlush(user); // 🔥 Il faut d'abord sauver l'utilisateur sans la tâche

        // 2. Ensuite seulement, supprimer la tâche
        repo.deleteById(id);
    }








}
